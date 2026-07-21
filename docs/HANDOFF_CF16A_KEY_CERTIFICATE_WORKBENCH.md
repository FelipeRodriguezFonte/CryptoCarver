# Handoff: CF-16A — Key & Certificate Format Workbench

## Resumen Ejecutivo
Se ha completado la implementación de la nueva herramienta transversal `Key & Certificate Format Workbench` para CryptoCarver. Esta herramienta permite inspeccionar, convertir y validar material de claves y certificados entre formatos habituales de laboratorio (PEM, DER, PKCS#8, SPKI, JWK, OpenSSH, PKCS#12) sin generar ni exportar secretos silenciosamente, y respetando rigurosamente las políticas de `SecretVisibility`.

## Trabajo Realizado

### 1. Infraestructura y Arquitectura
- **Módulo Independiente**: Se creó la herramienta desacoplada del `Key Material Inspector`.
  - `key_certificate_workbench.fxml`: Vista con áreas de entrada, botones de detección/conversión y visualización de resultados (Formato, Algoritmo, Subject, Issuer).
  - `KeyCertificateWorkbenchController`: Controlador que coordina la UI, inyecta `OperationResult` en el historial y se integra con `ClipboardShelfManager`.
  - `KeyCertificateFormatService`: Core agnóstico de UI para el parseo, conversión y validación criptográfica.
- **Registro**: La herramienta se registró bajo la categoría lateral "Tools" (`op_keys_format_workbench`) dentro de `OperationRegistry`.
- **Integración**: Funciona nativamente con el Historial (`OperationResult`), publicando el estado, el formato y la clave/certificado parseado.

### 2. Detección y Parsing
El motor de detección en `KeyCertificateFormatService` inspecciona los bytes crudos y deduce de manera robusta:
- **DER/PEM Certificates**: Certificados X.509.
- **DER/PEM Public Keys**: SPKI (SubjectPublicKeyInfo).
- **DER/PEM Private Keys**: PKCS#8.
- **JWK**: Claves públicas y privadas JSON (Soporte RSA y EC).
- **OpenSSH**: Claves públicas en formato OpenSSH.
- **PKCS#12**: Detección basada en ASN.1 SEQUENCE magic bytes e intento de inicialización con password vacío, diferenciando claves protegidas/cifradas de claves expuestas.
- Resuelve correctamente OIDs de BouncyCastle (ej. `1.2.840.10045.2.1` -> `EC`).

### 3. Conversión Explícita
Permite transformar de forma determinista entre los formatos soportados.
- **PEM <-> DER** (Base64 vs crudo) para certificados y claves.
- **PEM/SPKI/DER -> JWK** (Público y Privado).
- **PEM/SPKI/DER -> OpenSSH** (Solo Público).
- Control riguroso de `SecretVisibility`: Lanza excepción explícita si se intenta convertir (exportar) una clave privada mientras las reglas del entorno están en `MASKED` o `REDACTED`.

### 4. Validación de Pares
- **KeyPair Matching**: Implementada validación criptográfica creando una firma efímera (Dummy Signature) que comprueba si la clave pública puede verificar un payload firmado por la clave privada aportada. Esto evita fallos en los que los bytes codificados puedan diferir a nivel estructural pero representar el mismo par de claves.

### 5. Seguridad y Memoria
- El password del PKCS#12 se captura en un `PasswordField` real y se expurga explícitamente (`Arrays.fill(password, '\0')`) después de usarse.
- Los volcados al portapapeles / estantería interna publican la metadata pero respetan en todo momento la sensibilidad del `OperationDetail.Classification`.

### 6. Calidad y Pruebas
- `KeyCertificateFormatServiceTest`: Batería completa de pruebas de unidad validando el flujo criptográfico con BouncyCastle usando pares RSA/EC autogenerados. 100% verde.
- `KeyCertificateWorkbenchControllerUITest`: Pruebas de inicialización headless para garantizar que el inyector del `FXMLLoader` no rompa. 100% verde.
- Catálogo de operaciones regenerado vía script (`scripts/generate-operations-catalog.sh`) para incluir `op_keys_format_workbench`. Todos los tests de la build (incluyendo integración GPG) transcurren sin dependencias de este módulo.
- `mvn -q test`, `xmllint` y `git diff --check` ejecutados exitosamente.

## Estado de la Tarea
- **CF-16A**: **CERRADO Y COMPLETADO**.

## Próximos Pasos (Opcional - Siguientes Fases)
- **Fase B (Futuro)**: Extender soporte a más Keystores (JKS, BKS).
- **Fase C (Futuro)**: Ampliar el soporte de algoritmos Post-Quantum (PQC) en caso de que sean requeridos dentro del formato JWK (que actualmente no tiene OIDs estándar de IANA universales).
- Continuar con el resto de incidencias del Backlog (`ANTIGRAVITY_IMPLEMENTATION_BACKLOG.md`).
