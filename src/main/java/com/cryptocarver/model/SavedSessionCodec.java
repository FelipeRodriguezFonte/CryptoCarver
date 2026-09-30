package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.cryptocarver.ui.UiStateSnapshot;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON serialization and protection for saved workspaces, independent of JavaFX. */
public final class SavedSessionCodec {
    private static final Type SESSION_LIST_TYPE = new TypeToken<List<SavedSession>>() { }.getType();
    private static final String MARKER = "[REDACTED_SECRET]";
    private final Gson writer = new GsonBuilder().setPrettyPrinting().create();
    private final Gson reader = new Gson();

    public String serialize(List<SavedSession> sessions) { return writer.toJson(sessions == null ? List.of() : sessions); }

    public List<SavedSession> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<SavedSession> parsed = reader.fromJson(json, SESSION_LIST_TYPE);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    /** Creates a persisted session with sensitive fields either redacted or encrypted. */
    public SavedSession prepareForStorage(SavedSession source, char[] password) {
        if (source == null) {
            if (password != null) java.util.Arrays.fill(password, '\0');
            throw new IllegalArgumentException("Session is required");
        }
        SavedSession stored = new SavedSession(source.getName(), source.getOperation(), source.getUiState(), null);
        stored.setId(source.getId());
        stored.setTimestamp(source.getTimestamp());
        stored.setVersion(1);
        stored.setTrailRedacted(source.isTrailRedacted());
        Map<String, Object> safe = new LinkedHashMap<>();
        Map<String, Object> secrets = new LinkedHashMap<>();
        if (source.getUiState() != null) source.getUiState().forEach((key, value) -> {
            String field = key == null ? "" : key.substring(key.lastIndexOf('.') + 1);
            if (UiStateSnapshot.isHistorySensitiveField(field)) secrets.put(key, value);
            else safe.put(key, value);
        });
        boolean encrypt = password != null && (secrets.size() > 0 || source.getOperationLog() != null);
        if (encrypt) {
            byte[] salt = PasswordFieldCipher.randomBytes(PasswordFieldCipher.SALT_BYTES);
            byte[] nonce = PasswordFieldCipher.randomBytes(PasswordFieldCipher.NONCE_BYTES);
            byte[] plain = writer.toJson(new ProtectedPayload(secrets, source.getOperationLog())).getBytes(StandardCharsets.UTF_8);
            try {
                byte[] cipher = PasswordFieldCipher.encrypt(password, salt, nonce, plain, PasswordFieldCipher.ITERATIONS);
                stored.setProtectedFields(new SavedSession.ProtectedFields(PasswordFieldCipher.KDF,
                        PasswordFieldCipher.ITERATIONS, b64(salt), b64(nonce), b64(cipher)));
                secrets.keySet().forEach(key -> safe.put(key, MARKER));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Unable to protect saved session secrets", e);
            } finally {
                java.util.Arrays.fill(plain, (byte) 0);
                java.util.Arrays.fill(password, '\0');
            }
        } else {
            secrets.forEach((key, value) -> safe.put(key, MARKER));
            OperationSessionLog trail = source.getOperationLog();
            if (trail != null && !trail.isEmpty()) {
                stored.setOperationLog(RedactedTrail.from(trail));
                stored.setTrailRedacted(true);
            } else {
                stored.setTrailRedacted(false);
            }
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
        stored.setUiState(safe);
        return stored;
    }

    /** Decrypts all protected fields into a detached session; source is unchanged on failure. */
    public SavedSession restore(SavedSession source, char[] password) {
        if (source == null || source.getProtectedFields() == null) {
            if (password != null) java.util.Arrays.fill(password, '\0');
            return source;
        }
        SavedSession.ProtectedFields fields = source.getProtectedFields();
        byte[] plain = null;
        try {
            if (!PasswordFieldCipher.KDF.equals(fields.getKdf()) || fields.getIterations() < PasswordFieldCipher.ITERATIONS
                    || fields.getIterations() > 2_000_000) throw new IllegalArgumentException("Invalid protected session parameters");
            byte[] salt = Base64.getDecoder().decode(fields.getSalt());
            byte[] nonce = Base64.getDecoder().decode(fields.getNonce());
            byte[] ciphertext = Base64.getDecoder().decode(fields.getCiphertext());
            if (salt.length != PasswordFieldCipher.SALT_BYTES || nonce.length != PasswordFieldCipher.NONCE_BYTES)
                throw new IllegalArgumentException("Invalid protected session parameters");
            plain = PasswordFieldCipher.decrypt(password, salt, nonce, ciphertext, fields.getIterations());
            ProtectedPayload payload = reader.fromJson(new String(plain, StandardCharsets.UTF_8), ProtectedPayload.class);
            Map<String, Object> merged = new LinkedHashMap<>(source.getUiState() == null ? Map.of() : source.getUiState());
            if (payload != null && payload.secrets != null) merged.putAll(payload.secrets);
            SavedSession restored = new SavedSession(source.getName(), source.getOperation(), merged,
                    payload == null || payload.operationLog == null ? source.getOperationLog() : payload.operationLog);
            restored.setId(source.getId());
            restored.setTimestamp(source.getTimestamp());
            restored.setVersion(source.getVersion());
            restored.setTrailRedacted(source.isTrailRedacted());
            return restored;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Incorrect password or modified saved session", e);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException && e.getMessage() != null
                    && e.getMessage().startsWith("Incorrect password")) throw e;
            throw new IllegalArgumentException("Unable to restore protected session", e);
        } finally {
            if (plain != null) java.util.Arrays.fill(plain, (byte) 0);
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    public List<SavedSession> redactLegacyPlaintext(List<SavedSession> sessions) {
        List<SavedSession> cleaned = new ArrayList<>();
        for (SavedSession session : sessions) {
            // Versioned sessions were already redacted or encrypted when saved.
            if (session.getVersion() != 0) {
                cleaned.add(session);
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            if (session.getUiState() != null) session.getUiState().forEach((key, value) -> {
                String field = key == null ? "" : key.substring(key.lastIndexOf('.') + 1);
                safe.put(key, UiStateSnapshot.isHistorySensitiveField(field) ? MARKER : value);
            });
            SavedSession copy = new SavedSession(session.getName(), session.getOperation(), safe, null);
            copy.setId(session.getId());
            copy.setTimestamp(session.getTimestamp());
            copy.setVersion(1);
            cleaned.add(copy);
        }
        return cleaned;
    }

    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }
    private static final class ProtectedPayload {
        Map<String, Object> secrets;
        OperationSessionLog operationLog;
        ProtectedPayload(Map<String, Object> secrets, OperationSessionLog operationLog) {
            this.secrets = secrets; this.operationLog = operationLog;
        }
    }
}
