# Encargo 76 — mapa de fase 1

Base: 257dd2b. Estado: detenido antes de extraer por defecto previo de privacidad.
No se mueve ningún método ni clave en esta entrega.

| Método / dato | Propietario actual | Propietario previsto | Dependencias |
| --- | --- | --- | --- |
| formatAnalysisReport (1251–1349) | EncryptedFileAnalyzer | EncryptedFileAnalysisReportWriter | Path, List, AnalysisCandidate, formatPercent |
| writeHtmlReport (1399–1528) | EncryptedFileAnalyzer | EncryptedFileAnalysisReportWriter | Path, Files, UTF-8, List, AnalysisCandidate, formatPercent, htmlEscape |
| htmlEscape | EncryptedFileAnalyzer | escritor | Reemplazos de entidades; sin cambios de literales |
| formatPercent | EncryptedFileAnalyzer | compartido con escritor, decisión pendiente | Locale.ROOT; usado también por detalles del inspector |
| AnalysisCandidate | EncryptedFileAnalyzer (privado) | queda; acceso para escritor pendiente | Campos de ranking, evidencia y preview; confidencePercent mutable |
| analyze, writeAttemptLog, csvEscape, createAnalysisDirectory | EncryptedFileAnalyzer | quedan | Orquestación, CSV, Files, fecha del directorio |
| descifrado, opciones, entradas, ranking, evidencia y calidad | EncryptedFileAnalyzer | quedan en fase 1 | SymmetricCipher, DataConverter, codificaciones y CipherInputs |

El escritor necesitaría acceso a los candidatos privados. No se ha elegido ni implementado
un DTO o cambio de visibilidad porque el bloqueo precede a la extracción.
El informe sin candidatos se construye dentro de analyze y queda allí en el plan de fase 1.

## Idioma y contratos por clase

EncryptedFileAnalyzer no utiliza I18nService ni claves de bundles: los informes son
literales ingleses. Claves que se mueven: ninguna. Claves que permanecen en esta clase:
ninguna. Los literales de texto y HTML continuarían perteneciendo a los métodos del
escritor; las etiquetas de inspector y los mensajes de analyze seguirían en el analizador.
No se corrige el idioma del texto durante este encargo.

Se localizaron contratos de presencia por clase en SpecializedFeedbackHeadlessTest y
PaymentsValidationHeadlessTest. Ninguno asigna claves a EncryptedFileAnalyzer ni a sus
métodos de informes. Todos sus propietarios y claves permanecen intactos, sin
reasignaciones. Se revisaron además coincidencias de source.contains en
KeyboardShortcutsAndMockupCleanupTest, Ux28AddToShelfUITest,
ProcessDesignerSelectionCharacterizationUITest y ModernMainControllerUITest;
no requieren reasignación para estos métodos.

## Cobertura existente y hueco añadido

- EncryptedFileAnalyzerTest: CBC ganador y plaintext exacto, tres archivos, archivo
  vacío, AAD desactivado con GCM, ausencia de clave hexadecimal en informes.
- EncryptedFileAnalysisCharacterizationUITest: digest conjunto de informes, CSV,
  inspector y salida UI para CBC raw/Base64, contenedor independiente y GCM con tag
  separado; errores de tag inexistente y clave vacía. Normaliza directorio y timestamp.
  No se modifica ni se duplica su batería.
- Hueco: perfiles MASKED/REDACTED en report.txt y report.html. La nueva reproducción
  genera CBC con clave e IV inventados por Random(76), texto ASCII inventado y entrada RAW.
  Solo prueba el hueco de privacidad, sin duplicar los digests existentes.

La reproducción falla en la base intacta. No se fijan nuevos SHA-256: la regla de parada
por defecto previo impide consolidar esta salida como contrato. Véase el documento de fallos.
