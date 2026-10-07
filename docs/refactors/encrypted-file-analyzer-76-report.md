# Encargo 76 — registro de las dos paradas

Estado actual: archivos corregidos por autorización expresa; detenido en fase 0, paso 3,
por texto recuperado en Outcome.inspectorOutput. Véase la continuación al final.

## Parada original (registro hasta 8b2588f)

Rama: codex/encrypted-file-analyzer-1. Worktree: CryptoCarver-efa-1.
Base: 257dd2b. No se ejecutó Maven en el repositorio principal ni hubo ejecuciones
Maven simultáneas.

La caracterización previa reproduce exposición del texto recuperado en report.txt y
report.html bajo MASKED y REDACTED. Se detuvo el trabajo antes de extraer, según la
regla expresa del encargo. El defecto no se corrige. La nueva reproducción conserva
las aserciones estrictas y falla sobre producción intacta; la rama no tiene una suite verde.

## Líneas

| Archivo / método | Antes | Después |
| --- | ---: | ---: |
| EncryptedFileAnalyzer.java | 1633 | 1633 |
| analyze | 311 | 311 |
| EncryptedFileAnalysisReportWriter.java | No existe | No creado |

No se modifica ningún archivo de producción, test existente, crypto/, pom.xml ni los
componentes protegidos. No hay extracción que retirar.

## Seis puertas

| Fase | Puerta / comando | Informes | Pruebas | Fallos | Errores | Omitidas | Exit / estado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | G1: mvn -o -q test -Plow-cpu | — | — | — | — | — | No ejecutada: bloqueo previo |
| 1 | G2: mvn -o -q test -Plow-cpu -DrunUiTests=true | — | — | — | — | — | No ejecutada: bloqueo previo |
| 1 | G3: mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test | — | — | — | — | — | No ejecutada: bloqueo previo |
| 2 | G1: mvn -o -q test -Plow-cpu | — | — | — | — | — | No iniciada |
| 2 | G2: mvn -o -q test -Plow-cpu -DrunUiTests=true | — | — | — | — | — | No iniciada |
| 2 | G3: mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test | — | — | — | — | — | No iniciada |

Los recuentos de referencia proporcionados (G1 451/2920/1 omitida y G3 131/550/0)
no se presentan como ejecuciones de esta rama. No se invoca la excepción de GC de
ExpandedViewerLifecycleUITest: el fallo bloqueante pertenece a otra clase.

## Comprobaciones ejecutadas

Se borró target/surefire-reports antes de cada ejecución con resultados de tests.
Se contaron únicamente sus TEST-*.xml.

| Ejecución | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Reproducción + EncryptedFileAnalyzerTest + contratos de claves | 4 | 10 | 2 | 0 | 0 | 1 |
| Contratos de claves solos (SpecializedFeedbackHeadlessTest, PaymentsValidationHeadlessTest) | 2 | 4 | 0 | 0 | 0 | 0 |

Comando aislado de claves:

```sh
mvn -o -q test -Plow-cpu -Dtest=SpecializedFeedbackHeadlessTest,PaymentsValidationHeadlessTest
```

Tests existentes modificados: ninguno. Tests nuevos:
EncryptedFileAnalysisPrivacyReproductionTest (dos perfiles, seed 76, clave e IV
inventados, plaintext ASCII inventado). AppSettings aislado restaurado en finally;
Shelf e historial no se instancian ni mutan; temporales eliminados por @TempDir.
El incumplimiento confirmado en informes basta para detener; las otras superficies
no se declaran verificadas. La caracterización UI existente fue leída, no modificada
ni ejecutada. No se fijan nuevos SHA-256 de una salida que viola el contrato requerido.

## Hallazgos no corregidos e higiene

El preview recuperado se escribe sin protección del perfil en TXT y HTML. Las aserciones
de ausencia de clave pasan. No se corrige privacidad, idioma, ranking ni otro comportamiento.
Detalles en los mapas y documentos de fallos de ambas fases.

Se ejecutó literalmente el bloque de presupuestos del job quality-gates de
.github/workflows/ui-tests.yml: estilos inline FXML = 0; emojis = 325 de 325.
No se añadieron imágenes, archivos .local.md, DMG ni ejecutables. Java disponible:
21.0.8 en macOS ARM64; no se afirma validación en Linux con Java 17.

## Commits

Un único commit de parada previa, sin commits de extracción:
`test: reproduce encrypted file report privacy defect before refactor 76`.
Su identificador se obtiene con `git log -1 --format='%h %s'` en esta rama.


## Continuación — corrección de privacidad autorizada

Se mantiene la parada original como registro histórico. La autorización posterior permite
proteger únicamente los archivos, sin corregir exposición en otras superficies.

Paso 1: la reproducción se amplió a attempts.csv antes del arreglo (d7d2529).
1 informe / 2 pruebas / 2 fallos / 0 errores / 0 omitidas / exit 1. Ambos perfiles
fallaban en TXT, HTML y CSV; ausencia de clave aprobada.

Paso 2: ab8a9fc añade allowRecoveredTextPreview a CipherInputs. CipherController envía
AppSettings.isFullLab() al capturar entradas; el analizador no consulta AppSettings.
Sin decisión explícita el valor es false. Los previews de candidatos se sustituyen por
[REDACTED_SECRET]; TXT, HTML y los intentos exitosos CSV usan ese mismo valor. Los
intentos fallidos conservan preview vacío. No se alteran las métricas, ranking ni confianza.
La rama autorizada conserva el preview original sin transformarlo.

Se fijaron TXT/HTML FULL_LAB por SHA-256 sobre el código anterior y se verificó su test
antes del arreglo (1/1/0/0/0, exit 0). Normalización: ruta del directorio temporal,
timestamp del directorio de análisis y finales de línea; sin excepciones de JDK/provider
ni mapas de orden indefinido. Tras el arreglo, reproducción de archivos + FULL_LAB +
EncryptedFileAnalyzerTest: 2 informes / 7 pruebas / 0 fallos / 0 errores / 0 omitidas /
exit 0. TXT y HTML conservan los mismos digests; los tres archivos conservan la vista
previa bajo autorización FULL_LAB. CSV no fija textos de excepciones por portabilidad.

Paso 3: comprobación del resto de Outcome: 1 informe / 2 pruebas / 2 fallos / 0 errores /
0 omitidas / exit 1. Fallan exclusivamente los bytes de inspectorOutput por contener el
texto recuperado en ambos perfiles. reportText, estado, detalles, historyInput e
historyResult pasan las aserciones de ausencia de texto y clave. inspectorOutput no
contiene la clave inventada. El controlador lo transmite directamente a updateInspector.
No se modifica esa superficie; se detiene antes de puertas y refactors, como se solicitó.
Los demás componentes UI no se instancian ni se declaran verificados.

La reproducción de archivos sigue verde y todas sus aserciones originales permanecen.
La nueva reproducción del inspector falla intencionalmente: la suite completa no está verde.
La corrección autorizada se conserva. No se invoca la excepción GC.

### Nueve puertas — estado tras la continuación

Los comandos G1/G2/G3 son exactamente los registrados en la tabla original.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit / estado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | G1 | — | — | — | — | — | No ejecutada: parada paso 3 |
| 0 | G2 | — | — | — | — | — | No ejecutada: parada paso 3 |
| 0 | G3 | — | — | — | — | — | No ejecutada: parada paso 3 |
| 1 | G1 | — | — | — | — | — | No iniciada: parada fase 0 |
| 1 | G2 | — | — | — | — | — | No iniciada: parada fase 0 |
| 1 | G3 | — | — | — | — | — | No iniciada: parada fase 0 |
| 2 | G1 | — | — | — | — | — | No iniciada: parada fase 0 |
| 2 | G2 | — | — | — | — | — | No iniciada: parada fase 0 |
| 2 | G3 | — | — | — | — | — | No iniciada: parada fase 0 |

### Líneas y comprobaciones actuales

EncryptedFileAnalyzer.java: 1633 antes, 1639 después de la corrección.
analyze conserva 311 líneas. No hay clase escritora ni extracción de evidencia.
Tests anteriores al encargo modificados: ninguno. Se amplía únicamente la reproducción
creada en este encargo, sin relajar aserciones; se añade contrato FULL_LAB y auditoría de Outcome.
No se toca ninguno de los archivos protegidos. AppSettings aislado se restaura en finally,
Shelf e historial no se mutan y @TempDir borra todos los archivos de las pruebas.

Se repite el bloque exacto de higiene de quality-gates: FXML inline = 0; emojis = 325.
Maven siempre secuencial y solo en el worktree autorizado. Entorno de pruebas: Java 21.0.8,
macOS ARM64; no se afirma ejecución Linux/Java 17. No se fijan nuevos digests para fases
1 y 2 porque no se han iniciado.

### Commits de la continuación

- d7d2529 — test: extend encrypted file privacy reproduction to CSV preview
- ab8a9fc — fix: require caller authorization for encrypted file report previews
- Commit de auditoría y parada: `test: document encrypted file inspector privacy blocker`

La parada original está en 8b2588f. No hay commits de extracción. El commit de auditoría
se identifica con `git log -1 --format='%h %s'` al terminar esta continuación.
