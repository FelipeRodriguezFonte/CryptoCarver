# Encargo 76 — detenido por defecto previo

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
