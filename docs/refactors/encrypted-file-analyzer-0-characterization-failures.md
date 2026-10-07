# Fase 0 — continuación autorizada

## Paso 1: CSV antes del arreglo

Sobre 8b2588f, se amplía únicamente la reproducción a attempts.csv, incluida su columna
preview. Las aserciones existentes permanecen intactas.

Comando: `mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalysisPrivacyReproductionTest`.
Tras borrar target/surefire-reports: 1 informe / 2 pruebas / 2 fallos / 0 errores /
0 omitidas / exit 1. Cada perfil (MASKED y REDACTED) falla en las tres superficies:
report.txt, report.html y attempts.csv preview. Las aserciones de ausencia de clave pasan.
Este resultado precede al arreglo; no se modifica producción en este paso.
