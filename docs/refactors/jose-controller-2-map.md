# JOSEController — fase 2

Base: `1828` líneas, `102` métodos detectados (incluye constructores; excluye record). Medido por `python3 docs/refactors/jose_method_map.py`, ignorando llaves en literales/comentarios.

Destino: `JoseJweCoordinator` con `record View`, proveedor vivo de controles y `Supplier<StatusReporter>`. El controlador conserva API, FXML, navegación, listeners, importación y diálogos de fichero. Ningún coordinador recibe un controlador.

| Método (línea original) | Líneas | Estado FXML leído / modificado | Dependencias y efectos |
|---|---:|---|---|
| `handleGenerateJWE` L789 | 32 | Lee: jweCompressCheck, jweContentAlgoCombo, jweOutputArea, jwePayloadArea, jwePublicKeyArea, jweKeyAlgoCombo, jweKeyFormatCombo, jweSerializationCombo, jweAadField, jwePbes2IterField, jweKidField, jweTypField, jweCtyField, jweApuField, jweApvField, jweCustomHeaderArea. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleDecryptJWE` L823 | 20 | Lee: jwePrivateKeyArea, jweIVArea, jweDecodedHeaderArea, jweStatusLabel, jweCiphertextArea, jweAuthTagArea, jweInputArea, jweDecodedPayloadArea, jweHeaderArea, jweEncryptedKeyArea, jweDecryptedKeyArea, jweDecryptKeyFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `generateJWE` L1075 | 35 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `decryptJWE` L1111 | 136 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `buildJweDecryptionResult` L1248 | 3 | Lee: argumentos. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `buildJweDecryptionResult` L1252 | 12 | Lee: argumentos. Modifica: salidas por argumento / retorno | I18n vivo EN/ES; avisos públicos no bloqueantes |
| `directCekPreviewMessage` L1265 | 3 | Lee: argumentos. Modifica: salidas por argumento / retorno | servicio JOSE / lógica de transformación; ver firma para salidas por parámetro |

Estado común: no hay claves cargadas independientes: viven en TextArea; no se copia material a campos nuevos. AppSettings se consulta mediante las políticas existentes; el reporte publicado se entrega al shell, sin caché en coordinadores. Los helpers de validación y avisos son comunes y el reporter se resuelve en cada uso.

Lógica pura: construcción/lectura JOSE y formato con servicios existentes. UI: lectura de controles, validación, colores, publicación y diálogos. No se cambia crypto/, OperationResult, StatusReporter ni las políticas del shell.

Caracterización: claves inventadas, salidas aleatorias comprobadas semánticamente y normalizadas antes del SHA-256; errores de proveedor solo comprobados como legibles, no incluidos textualmente. EN/ES y FULL_LAB/MASKED/REDACTED. Tests existentes intactos; cualquier fallo se anota antes del arreglo en el registro de la fase.
