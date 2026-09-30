# Encargo 42: inventario previo (base 08ec1be)

Inventario obtenido con `rg -n 'PasswordFieldCipher|ProtectedFields|ProtectedPayload|prepareForStorage|codec.restore' src` y búsqueda de `SavedSession` y `saved_sessions` en toda la producción.

## Mutaciones y flujos

| Caso | Comportamiento actual | id / operation / claves protegidas |
| --- | --- | --- |
| Guardar | El coordinador crea una sesión; el codec copia identidad y separa secretos | Nueva identidad antes de cifrar; luego estables |
| Cargar / vista previa | El gestor deserializa; la vista previa solo lee; restore devuelve una copia | Conservados; restore repone valores sin modificar el original |
| Renombrar | No existe flujo ni setter de nombre | Sin cambio legítimo con ciphertext vigente |
| Duplicar | No existe flujo; añadir un objeto cifrado al gestor conserva el objeto | Sin regeneración de id |
| Importar / exportar | No existe flujo dedicado; serialize/deserialize y migración del fichero antiguo copian datos | Conservados |
| Fusionar | No existe flujo | Sin cambio legítimo |
| timestamp / version | Setters públicos; codec copia timestamp y normaliza version al guardar; restore copia ambos | No afectan identidad, operación ni claves; excluidos del AAD |
| Eliminar secretos de las sesiones guardadas | redactLegacyPlaintext solo transforma version 0; versionadas cifradas quedan intactas | Conservados; las antiguas en claro se redactan sin ciphertext |
| Borrar otra sesión / persistir lista | Se serializa la lista; no se recifran sesiones existentes | Conservados |
| Volver a guardar estado cargado | Se captura una sesión nueva y se cifra de nuevo | Cambios legítimos ocurren antes de nuevo cifrado |

## Formato actual

ProtectedFields: kdf (String), iterations (int), salt, nonce, ciphertext (String Base64).
Gson serializa campos privados; omite referencias null por defecto. Campos añadidos ausentes en JSON antiguo quedan en 0 para int y false para boolean. ProtectedPayload contiene secrets (mapa) y operationLog (opcional). El antiguo constructor se conservará.

## AAD propuesto

Etiqueta de formato y versión 1, id (incluido null inequívoco), operation, claves ordenadas de uiState cuyo valor es exactamente [REDACTED_SECRET], e indicador de rastro cifrado. Se añadirá protectedTrail al sobre para reconstruir este último antes de descifrar; su valor quedará autenticado por GCM. Codificación binaria con longitudes UTF-8, contador de claves y boolean; null se distingue de cadena vacía.

Las claves se reconstruyen de los marcadores recibidos, no de una lista copiada desde el sobre: quitar, añadir o renombrar un marcador debe fallar. Los valores seguros no forman parte del AAD. Nombre, timestamp y version de sesión quedan fuera porque no identifican el ciphertext y pueden ser modificados sin recifrar. El cifrado no garantiza la integridad de todo uiState ni de los metadatos excluidos.

Los flujos actuales no modifican legítimamente los datos incluidos con ciphertext vigente. Un futuro duplicado con id nuevo necesitará descifrar y recifrar.

## Usos encontrados (antes del cambio)

```text
src/main/java/com/cryptocarver/model/ScreenConfigurationCodec.java:38:            ciphertext = PasswordFieldCipher.encrypt(password, salt, nonce, plaintext, ITERATIONS, aad());
src/main/java/com/cryptocarver/model/ScreenConfigurationCodec.java:89:            plaintext = PasswordFieldCipher.decrypt(password, salt, nonce, ciphertext, envelope.iterations, aad());
src/main/java/com/cryptocarver/model/ScreenConfigurationCodec.java:181:        return PasswordFieldCipher.randomBytes(length);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:19:        SavedSession stored = codec.prepareForStorage(source(), null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:21:        SavedSession restored = codec.restore(codec.deserialize(codec.serialize(List.of(stored))).get(0), null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:31:        SavedSession stored = codec.prepareForStorage(original, "invented-password-41".toCharArray());
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:33:        assertNotNull(stored.getProtectedFields());
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:35:        SavedSession restored = codec.restore(parsed, "invented-password-41".toCharArray());
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:43:        SavedSession restored = codec.restore(stored, null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:51:        String json = codec.serialize(List.of(codec.prepareForStorage(source(), null)));
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:78:        SavedSession absent = codec.prepareForStorage(new SavedSession("Empty", "Synthetic", Map.of()), null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:79:        SavedSession empty = codec.prepareForStorage(new SavedSession("Empty", "Synthetic", Map.of(),
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:89:        SavedSession once = codec.prepareForStorage(source(), null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:90:        SavedSession twice = codec.prepareForStorage(once, null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:98:        assertNull(codec.restore(stored, null).getOperationLog());
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:104:        SavedSession redacted = codec.prepareForStorage(source(), null);
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:105:        SavedSession encrypted = codec.prepareForStorage(redacted, "invented-password-41".toCharArray());
src/test/java/com/cryptocarver/model/SavedSessionTrailStorageTest.java:106:        SavedSession restored = codec.restore(encrypted, "invented-password-41".toCharArray());
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:46:        String json = codec.serialize(List.of(codec.prepareForStorage(new SavedSession("Lab", "Cipher",
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:57:        SavedSession saved = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:71:        SavedSession stored = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:77:        SavedSession restored = codec.restore(parsed, "correct horse".toCharArray());
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:80:        assertThrows(IllegalArgumentException.class, () -> codec.restore(parsed, "wrong horse".toCharArray()));
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:86:        assertThrows(IllegalArgumentException.class, () -> codec.restore(tampered, "correct horse".toCharArray()));
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:105:        SavedSession encrypted = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
src/test/java/com/cryptocarver/model/SavedSessionCodecTest.java:109:        assertThrows(IllegalArgumentException.class, () -> codec.restore(encrypted, wrongPassword));
src/main/java/com/cryptocarver/model/SavedSession.java:20:    private ProtectedFields protectedFields;
src/main/java/com/cryptocarver/model/SavedSession.java:72:    public ProtectedFields getProtectedFields() { return protectedFields; }
src/main/java/com/cryptocarver/model/SavedSession.java:73:    public void setProtectedFields(ProtectedFields protectedFields) { this.protectedFields = protectedFields; }
src/main/java/com/cryptocarver/model/SavedSession.java:75:    public static final class ProtectedFields {
src/main/java/com/cryptocarver/model/SavedSession.java:82:        public ProtectedFields() { }
src/main/java/com/cryptocarver/model/SavedSession.java:83:        public ProtectedFields(String kdf, int iterations, String salt, String nonce, String ciphertext) {
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:33:    public SavedSession prepareForStorage(SavedSession source, char[] password) {
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:52:            byte[] salt = PasswordFieldCipher.randomBytes(PasswordFieldCipher.SALT_BYTES);
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:53:            byte[] nonce = PasswordFieldCipher.randomBytes(PasswordFieldCipher.NONCE_BYTES);
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:54:            byte[] plain = writer.toJson(new ProtectedPayload(secrets, source.getOperationLog())).getBytes(StandardCharsets.UTF_8);
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:56:                byte[] cipher = PasswordFieldCipher.encrypt(password, salt, nonce, plain, PasswordFieldCipher.ITERATIONS);
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:57:                stored.setProtectedFields(new SavedSession.ProtectedFields(PasswordFieldCipher.KDF,
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:58:                        PasswordFieldCipher.ITERATIONS, b64(salt), b64(nonce), b64(cipher)));
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:83:        if (source == null || source.getProtectedFields() == null) {
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:87:        SavedSession.ProtectedFields fields = source.getProtectedFields();
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:90:            if (!PasswordFieldCipher.KDF.equals(fields.getKdf()) || fields.getIterations() < PasswordFieldCipher.ITERATIONS
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:95:            if (salt.length != PasswordFieldCipher.SALT_BYTES || nonce.length != PasswordFieldCipher.NONCE_BYTES)
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:97:            plain = PasswordFieldCipher.decrypt(password, salt, nonce, ciphertext, fields.getIterations());
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:98:            ProtectedPayload payload = reader.fromJson(new String(plain, StandardCharsets.UTF_8), ProtectedPayload.class);
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:143:    private static final class ProtectedPayload {
src/main/java/com/cryptocarver/model/SavedSessionCodec.java:146:        ProtectedPayload(Map<String, Object> secrets, OperationSessionLog operationLog) {
src/test/java/com/cryptocarver/model/SavedSessionsManagerTest.java:52:        SavedSession encrypted = codec.prepareForStorage(new SavedSession("protected", "Cipher",
src/test/java/com/cryptocarver/model/SavedSessionsManagerTest.java:60:        assertNotNull(kept.getProtectedFields());
src/test/java/com/cryptocarver/model/SavedSessionsManagerTest.java:61:        assertEquals("invented-protected-secret", codec.restore(kept, "long enough".toCharArray())
src/test/java/com/cryptocarver/model/SavedSessionsManagerTest.java:69:        SavedSession prepared = codec.prepareForStorage(SavedSessionTrailStorageTest.source(), null);
src/main/java/com/cryptocarver/model/SavedSessionsManager.java:55:            SavedSession prepared = session.getProtectedFields() == null
src/main/java/com/cryptocarver/model/SavedSessionsManager.java:56:                    ? codec.prepareForStorage(session, null) : session;
src/main/java/com/cryptocarver/model/PasswordFieldCipher.java:13:final class PasswordFieldCipher {
src/main/java/com/cryptocarver/model/PasswordFieldCipher.java:21:    private PasswordFieldCipher() { }
src/main/java/com/cryptocarver/ui/SavedSessionsCoordinator.java:159:        manager.addSession(codec.prepareForStorage(source, password));
src/main/java/com/cryptocarver/ui/SavedSessionsCoordinator.java:184:        if (session.getProtectedFields() != null) {
src/main/java/com/cryptocarver/ui/SavedSessionsCoordinator.java:193:                try { restored = codec.restore(session, password.getText().toCharArray()); }
src/main/java/com/cryptocarver/ui/SessionTrailViewFormatter.java:57:        if (session.getProtectedFields() != null) {
src/test/java/com/cryptocarver/ui/SessionTrailViewFormatterTest.java:33:        encrypted.setProtectedFields(new SavedSession.ProtectedFields("synthetic-kdf", 1, "salt", "nonce", "ciphertext"));
src/test/java/com/cryptocarver/ui/SessionTrailViewFormatterTest.java:78:        SavedSession stored = new com.cryptocarver.model.SavedSessionCodec().prepareForStorage(
```
