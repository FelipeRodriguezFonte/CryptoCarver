# Encargo 76 — mapa de fase 1

Registro inicial (parada conservada). Base: 257dd2b. Estado en aquella ejecución: detenido antes de extraer por defecto previo de privacidad.
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

## Continuación autorizada: nuevo bloqueo de fase 0

La exposición en TXT/HTML/CSV se corrige mediante autorización explícita en CipherInputs,
calculada por CipherController con AppSettings.isFullLab(). El analizador no lee AppSettings.
El mapa de extracción no cambia: no se mueven métodos ni claves. La fase 0 se detiene en
su paso 3 porque Outcome.inspectorOutput sigue exponiendo texto recuperado bajo MASKED
y REDACTED. Véase encrypted-file-analyzer-0-characterization-failures.md y el informe 76.


## Mapa operativo de la segunda continuación (antes de extraer)

Base de producción: privacidad de archivos corregida en ab8a9fc, comprobación de inspector
real en 1df985e. Las puertas de fase 0 preceden a la extracción. Se mantienen los
contratos lingüísticos descritos arriba: ninguna clave se mueve, ninguna reasignación
a tests existentes. SpecializedFeedbackHeadlessTest y PaymentsValidationHeadlessTest
se ejecutarán aislados antes de las puertas de esta fase.

| Elemento | Destino | Dependencias / decisión |
| --- | --- | --- |
| formatAnalysisReport | EncryptedFileAnalysisReportWriter (static, paquete) | Cuerpo intacto; AnalysisCandidate, Path, List; llama al formatPercent compartido |
| writeHtmlReport | EncryptedFileAnalysisReportWriter (static, paquete) | Cuerpo intacto; Files, UTF-8, Path, List, candidato, htmlEscape y formatPercent |
| htmlEscape | Escritor (private static) | Cuerpo intacto, solo escape de entidades |
| formatPercent | Analizador (static, paquete) | Mismo String.format y Locale.ROOT; compartido con los detalles del inspector |
| AnalysisCandidate | Analizador, clase anidada con acceso de paquete | Solo los campos consumidos por el escritor tienen acceso de paquete; constructor, plaintext y paddingEvidence permanecen privados |
| ranking, porcentajes, evidencia, descifrado, entradas/opciones | Analizador | Sin traslados ni cambios de lógica |
| CSV, directorio, informe sin candidatos | Analizador | Sin traslado en fase 1 |

El escritor no modifica candidatos ni la confianza. No recibe settings ni decide
privacidad; consume la vista previa ya protegida por la fase 0.

Caracterización sobre código corregido: se reutilizan los SHA-256 FULL_LAB de la
reproducción y los casos UI existentes. Se fijan además TXT/HTML protegidos dentro
de la reproducción ya creada y se añade exclusivamente muestreo/sin candidatos
(EncryptedFileAnalysisRefactorCharacterizationTest, semilla 7601). No se duplican CBC,
Base64, contenedor o GCM. Se normalizan ruta, timestamp del directorio y CRLF; no se
fijan excepciones del CSV ni iteración de mapas. Ningún test nuevo cambia Shelf o historia;
la fixture de inspector real restaura todas las superficies que sí utiliza.


Extracción aplicada según este mapa: analizador 1639 → 1396 líneas; escritor nuevo
257 líneas. Se conservan todos los literales Java. Caracterización focal antes y después:
5 informes / 19 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Contratos de claves aislados antes de puertas: 2 informes / 4 pruebas / 0 fallos /
0 errores / 0 omitidas / exit 0. Ningún propietario de clave cambia.


Puertas de fase 1 aprobadas sin excepción GC:
G1 454 informes / 2929 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0;
G2 y G3, cada una, 132 informes / 552 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borraron los informes antes de cada puerta. No se retira la extracción porque las tres pasan.
