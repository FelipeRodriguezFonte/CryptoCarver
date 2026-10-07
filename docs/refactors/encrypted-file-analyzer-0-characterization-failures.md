# Fase 0 — continuación autorizada

## Paso 1: CSV antes del arreglo

Sobre 8b2588f, se amplía únicamente la reproducción a attempts.csv, incluida su columna
preview. Las aserciones existentes permanecen intactas.

Comando: `mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalysisPrivacyReproductionTest`.
Tras borrar target/surefire-reports: 1 informe / 2 pruebas / 2 fallos / 0 errores /
0 omitidas / exit 1. Cada perfil (MASKED y REDACTED) falla en las tres superficies:
report.txt, report.html y attempts.csv preview. Las aserciones de ausencia de clave pasan.
Este resultado precede al arreglo; no se modifica producción en este paso.

## Paso 2: corrección autorizada de archivos

CipherInputs recibe allowRecoveredTextPreview. El constructor sin decisión explícita
usa false. CipherController envía AppSettings.isFullLab(), leído al capturar entradas.
EncryptedFileAnalyzer no consulta AppSettings. Tras evaluar calidad y puntuación como
antes, guarda preview original si está autorizado o [REDACTED_SECRET] en otro caso.
Los tres escritores reciben ese mismo preview protegido. No cambia algoritmo, modo,
padding, codificación inferida, puntuación, confianza, orden ni plaintext del candidato.

Antes de modificar producción se fijaron SHA-256 de TXT y HTML FULL_LAB normalizados
(solo ruta, timestamp del directorio y finales de línea). Se comprobó que el test pasa
sobre producción sin arreglo: 1 informe / 1 prueba / 0 fallos / 0 errores / 0 omitidas /
exit 0. La captura inicial con valores provisionales falló solo al recoger ambos hashes;
no fue un defecto de producción. No se fijan mensajes de excepción de CSV.

Tras el arreglo:
`mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalysisPrivacyReproductionTest,EncryptedFileAnalyzerTest`
produce 2 informes / 7 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borraron los XML antes de cada ejecución. Las aserciones de reproducción de archivos
son las mismas. El test FULL_LAB conserva previews en los tres archivos y los hashes
anteriores de TXT/HTML. Su autorización true atraviesa la rama que conserva quality.preview
sin transformar; CSV conserva el escritor original. Comprobación de otras superficies pendiente.
