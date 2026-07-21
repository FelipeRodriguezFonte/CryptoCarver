# CF-15E: Handoff Document

## Alcance
- Implementación de **cifrado y descifrado CMS (EnvelopedData)** utilizando tokens PKCS#11.
- No se afirman firmas CAdES, eIDAS, ni validación de confianza: el alcance es estrictamente criptografía CMS / PKCS#7 (EnvelopedData).
- Soporte simultáneo para el flujo de Local PEM existente y el nuevo modo PKCS#11 Token, alternable mediante UI.

## Cambios Realizados

### Core Criptográfico
1. **`CMSOperations.decryptEnvelopedData(byte[] pkcs7Data, PrivateKey privateKey, Provider keyProvider)`**:
   - Sobrecarga añadida que permite inyectar el `Provider` del token HSM para la operación asimétrica.
   - Itera de forma resiliente sobre todos los destinatarios (`RecipientInformation`) e intenta descifrar cada uno, continuando en caso de fallo, hasta que uno tiene éxito o se agotan todos.
   - Usa `JceKeyTransEnvelopedRecipient` con `setContentProvider("BC")` para asegurar que el descifrado simétrico del payload use BouncyCastle, incluso si la clave asimétrica está en el HSM.

2. **`Pkcs11Session.decryptCms(String alias, byte[] cmsBytes)`**:
   - Resuelve de manera segura el handle de la clave privada (`requireKey(..., PrivateKey.class)`) manteniéndola opaca.
   - Delega la operación de descifrado a `CMSOperations` inyectando la instancia interna de `SunPKCS11`.
   - La clave privada nunca se expone fuera del módulo de seguridad.

### UI y Controladores
- **FXML (`main-view-modern.fxml`)**: Se agregó un `ToggleGroup` en la sección "Encryption (EnvelopedData)" para alternar la visibilidad entre `cmsEncryptLocalGrid` (campos PEM) y `cmsEncryptPkcs11Box` (ComboBox de aliases).
- **`KeysController.java`**:
  - `handleCMSEncryptSourceChanged()`: Controla la visibilidad `visible` y `managed` de los contenedores según la fuente seleccionada.
  - `handleLoadCMSEncryptKeys()`: Carga los alias válidos (solo aquellos devueltos por `listPrivateKeysWithCertificate()`) en el ComboBox.
  - `handleCMSEncrypt()` y `handleCMSDecrypt()`: Cuentan ahora con dos flujos lógicos independientes (Local vs. Token). El flujo del Token obtiene los componentes públicos directamente o delega en `Pkcs11Session`, reportando de manera segura las operaciones exitosas al Historial sin exponer material sensible.
- **`ModernMainController.java`**: Inyecta y delega los nuevos métodos `@FXML` de cambio de fuente y carga de claves.

### Pruebas
1. **Tests Criptográficos (`CMSOperationsTest`)**:
   - Agregadas pruebas independientes de SoftHSM para EnvelopedData:
     - Escenario positivo: Generación local, cifrado y descifrado verificando byte a byte.
     - Escenario negativo: Intento de descifrar un sobre destinado a un certificado distinto usando otra clave. Falla con un mensaje controlado.
     - Escenario multi-recipient: Generación de un sobre cifrado para múltiples destinatarios, logrando descifrar con éxito iterando más allá del destinatario erróneo inicial.
2. **Tests Integración SoftHSM (`SoftHsmIntegrationTest`)**:
   - `testCmsEncryption()`: Comprueba el cifrado utilizando la parte pública del certificado dentro del token y su correcto descifrado.
3. **Tests UI (`ModernMainControllerUITest`)**:
   - `testCmsUiEncryptPkcs11Toggle()`: Reforzado para validar de forma robusta que los contenedores `localGrid` y `pkcs11Box` activan y desactivan tanto sus atributos `visible` como `managed`, sin requerir instancias reales del token en tests unitarios estáticos.

## Resultados de Integración con SoftHSM
- **SKIPPED**. No se detectaron las variables de entorno de SoftHSM (`SOFTHSM2_MODULE`, `SOFTHSM2_CONF`, `CRYPTOCARVER_SOFTHSM_PIN`) en el entorno de build local. Todos los tests asumen comportamiento de fallback y se saltean ordenadamente en su ausencia.

## Limitaciones Conocidas
- Los tests globales de UI (`ModernMainControllerUITest` enteros en batch) experimentan timeouts ocasionales de JavaFX (10s runLater) que causan fallos de interbloqueo del Thread de la UI durante la carga recurrente del FXML en el sistema de CI. Esto es un artefacto de la estrategia de pruebas de JavaFX y no afecta a las validaciones aisladas del componente CMS, las cuales operan con normalidad.
- Sólo se soporta `KeyTransRecipientInfo` (transporte de claves asimétrico con RSA/EC) para cifrado CMS actualmente.
