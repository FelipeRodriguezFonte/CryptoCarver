# Fase 2 — no iniciada

La fase 1 encontró una exposición previa del texto recuperado bajo MASKED y REDACTED
en ambos informes. Conforme a la regla de parada, la fase 2 no se caracteriza ni extrae.
No hay fallos nuevos de fase 2 ni digests fijados. La reproducción y los recuentos están
en encrypted-file-analyzer-1-characterization-failures.md.


## Segunda continuación — antes del troceado

Base de fase 2: c6ea897, escritor extraído y tres puertas de fase 1 limpias.
Se reutiliza la caracterización fijada antes de fase 1 sobre producción corregida,
sin duplicar tests ni hashes: inputs generados con semilla 76/7601; TXT/HTML FULL_LAB,
protegidos, muestreo y sin candidatos; batería UI original para CBC/Base64/contenedor/GCM.
Las normalizaciones y exclusiones portables son las del mapa de fase 1.

Comando, todavía sin trocear:
`mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalyzerTest,EncryptedFileAnalysisCharacterizationUITest,EncryptedFileAnalysisPrivacyReproductionTest,EncryptedFileAnalysisRefactorCharacterizationTest,EncryptedFileAnalysisInspectorPrivacyUITest`
5 informes / 19 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borró target/surefire-reports antes de ejecutar. No se observaron defectos nuevos
ni discrepancias de digest antes de extraer. No se toca ningún test ni umbral en fase 2.


Después del troceado: la misma batería focal da 5 informes / 19 pruebas / 0 fallos /
0 errores / 0 omitidas / exit 0, con los mismos hashes. Contratos de claves aislados
antes de puertas: 2 informes / 4 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se conserva el conjunto de literales Java; el resto del archivo desde sus constantes
(evidencia, ranking, helpers de descifrado y CSV) es byte a byte idéntico a c6ea897.
Líneas: analyze 311 → 37; archivo 1396 → 1437 (se añaden límites de métodos y estado local).
No se mueve la evidencia a otra clase por la dependencia descrita; no se toca su lógica.


Puertas de fase 2 aprobadas sin excepción GC:
G1 454 informes / 2929 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0;
G2 y G3, cada una, 132 informes / 552 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borraron los informes antes de cada puerta. Todas pasan: se conserva el troceado.
