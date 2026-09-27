# Seguridad de las sesiones guardadas

Las sesiones guardadas se almacenan en `~/.cryptocarver/saved_sessions.json`. El estado sensible de controles se detecta con la misma política `UiStateSnapshot.isHistorySensitiveField` que usa el histórico. De forma predeterminada esos valores se sustituyen por `[REDACTED_SECRET]`. El histórico operativo no se persiste en claro; al activar la inclusión de secretos, también queda dentro del bloque cifrado.

La casilla **Incluir secretos (cifrados con contraseña)** está desmarcada inicialmente y no está disponible con el perfil `REDACTED`. La contraseña se pide dos veces, debe tener al menos 8 caracteres y no se almacena. Los campos restantes siguen legibles en JSON. Si se cancela la contraseña al restaurar, se recupera el resto del estado y los secretos quedan vacíos al aplicarse el marcador.

## Formato

Una sesión redactada conserva el esquema versionado:

```json
{
  "version": 1,
  "name": "Ejemplo",
  "operation": "Cipher",
  "uiState": {
    "CipherController.symmetricKeyField": "[REDACTED_SECRET]",
    "CipherController.modeCombo": "GCM"
  }
}
```

Cuando el usuario elige proteger secretos, `protectedFields` contiene los valores sensibles serializados y el histórico de esa sesión, autenticados y cifrados con AES-256-GCM. El resto de `uiState` permanece legible:

```json
{
  "version": 1,
  "name": "Ejemplo",
  "operation": "Cipher",
  "uiState": {
    "CipherController.symmetricKeyField": "[REDACTED_SECRET]",
    "CipherController.modeCombo": "GCM"
  },
  "protectedFields": {
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 600000,
    "salt": "AAECAwQFBgcICQoLDA0ODw==",
    "nonce": "AAECAwQFBgcICQoL",
    "ciphertext": "BASE64_CIPHERTEXT_AND_GCM_TAG"
  }
}
```

Los valores Base64 del ejemplo son ficticios y no forman un sobre descifrable. Las sesiones sin `version` siguen aceptándose como formato anterior. Se avisa cuando hay secretos en claro en sesiones antiguas y la acción de limpieza reemplaza los campos sensibles por marcadores y elimina el histórico operativo asociado, tras confirmación. Solo afecta a las sesiones sin versión; las guardadas con el formato nuevo, cifradas o no, se conservan intactas.

La primitiva criptográfica compartida `PasswordFieldCipher` reutiliza los algoritmos ya empleados por `ScreenConfigurationCodec`; no se modifican algoritmos de `crypto/`. La exportación de configuración de pantalla en JSON sin cifrar redacta los campos sensibles; la exportación cifrada es la opción explícita que los conserva, protegidos con la contraseña. El descifrado de configuraciones antiguas mantiene compatibilidad con sus iteraciones históricas; las escrituras nuevas usan 600.000.
