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

Digests fijados y verificados antes de extracción:
- Serializaciones/opciones/privacidad: `898aa2dc9da06352c580966e0a54dee58d8db86d94ea2bcc3febe5beb7d42401`.
- Algoritmos/avisos/CEK/errores: `784d6a61e011dee39d8c2c5f6798bd4a29484f2fbc86d8f041d4e39ed69272fd`.

Caracterización destapó PUBLIC por omisión en plaintext Compact y JSON; ambas corregidas como SECRET, en commits separados y antes de fijar los digests. Las cuatro pruebas JOSE originales no se modifican.

Extracción: 1828 → 1608 líneas en JOSEController. Se extraen 7 métodos, sin mover listeners ni selectores del arranque. Ambos digests se mantienen tras extracción.

Aserción fuente obsoleta prevista: `module.jose.feedback.statusJweDecrypted` pasa de JOSEController a JoseJweCoordinator. SpecializedFeedbackHeadlessTest comprobará el mismo contrato de presencia/traducciones EN/ES en el nuevo propietario, sin relajar ninguna aserción. Las pruebas JOSEInspector/JweSecurity/JwkConversion no cambian. Nota de ejecución: `-DrunUiTests=true` activa el perfil con groups=ui, por lo que las pruebas headless se verifican en la puerta default sin esa propiedad explícita.

Puerta final: default 2895 tests (0 fallos/errores, 1 omitido), UI 534 (0 fallos/errores/omitidos), CI 534 (3 fallos, 0 errores/omitidos). El CI se contó sobre 118 XML recién generados, tras borrar su directorio; sus resultados están preservados en jose-controller-validation.json antes de las repeticiones. Solo falla ExpandedViewerLifecycleUITest. Repetición aislada con las mismas opciones CI: 3/3 fallos. Comparación con JOSEController restaurado temporalmente desde main 4e44749 en este worktree: los mismos 3/3 fallos; las clases de ventanas, ModernMainController y los dos tests de ciclo de vida son idénticos a main. Los nuevos coordinadores permanecen en el classpath y no intervienen en estos tests. Se restauró el controlador extraído al terminar. No se ejecutó la suite completa de main. El fallo reproducido es retención de Stage al comprobar WeakReference, no un fallo JOSE. La puerta CI sigue fallida: fase 2 parcial, fases 3 y 4 no iniciadas según la regla de parada.
