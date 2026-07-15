package com.cryptoforge.crypto;

import com.cryptoforge.util.DataConverter;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/** Reads keystore metadata without retrieving or exporting private key material. */
public final class KeyStoreInspector {
    private KeyStoreInspector() { }

    public record Entry(String alias, String kind, String algorithm, String fingerprint, String subject, String keyMaterial) { }
    public record Report(String type, List<Entry> entries) { }

    public static Report inspect(Path path, char[] password, String requestedType) throws Exception {
        return inspect(path, password, requestedType, false);
    }

    /**
     * Inspects a store and, only when explicitly requested for this laboratory,
     * exposes exportable key encodings. Non-exportable token keys remain hidden.
     */
    public static Report inspect(Path path, char[] password, String requestedType, boolean extractInsecurely) throws Exception {
        if (path == null || !Files.isRegularFile(path)) throw new IllegalArgumentException("Keystore file does not exist");
        char[] safePassword = password == null ? new char[0] : password.clone();
        try {
            KeyStore store = load(path, safePassword, requestedType);
            List<Entry> entries = new ArrayList<>();
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                String kind = store.isKeyEntry(alias) ? "Key entry" : store.isCertificateEntry(alias) ? "Certificate entry" : "Other entry";
                java.security.cert.Certificate certificate = store.getCertificate(alias);
                String algorithm = certificate == null ? "Not exposed" : certificate.getPublicKey().getAlgorithm();
                String fingerprint = certificate == null ? "Not exposed" : KeyMaterialInspector.fingerprint(certificate.getEncoded());
                String subject = certificate instanceof X509Certificate x509 ? x509.getSubjectX500Principal().getName() : "";
                String material = "Not requested";
                if (extractInsecurely && store.isKeyEntry(alias)) {
                    java.security.Key key = store.getKey(alias, safePassword);
                    if (key == null || key.getEncoded() == null) material = "Not exportable by this KeyStore/provider";
                    else material = DataConverter.bytesToHex(key.getEncoded());
                }
                entries.add(new Entry(alias, kind, algorithm, fingerprint, subject, material));
            }
            return new Report(store.getType(), List.copyOf(entries));
        } finally {
            Arrays.fill(safePassword, '\0');
        }
    }

    private static KeyStore load(Path path, char[] password, String requestedType) throws Exception {
        String[] candidates = requestedType == null || "Auto".equals(requestedType)
                ? new String[] { "PKCS12", "JKS", "JCEKS" } : new String[] { requestedType };
        Exception last = null;
        for (String type : candidates) {
            try (InputStream input = Files.newInputStream(path)) {
                KeyStore store = KeyStore.getInstance(type);
                store.load(input, password);
                return store;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalArgumentException("Cannot open keystore as " + String.join(", ", candidates)
                + ". Check type and password.", last);
    }
}
