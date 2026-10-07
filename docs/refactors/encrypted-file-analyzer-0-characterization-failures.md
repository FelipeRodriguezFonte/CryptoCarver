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

## Paso 3: parada por inspector

Tras la corrección de archivos (ab8a9fc), se añade una comprobación independiente de todas
las superficies de Outcome indicadas. Comando:

```sh
mvn -o -q test -Plow-cpu '-Dtest=EncryptedFileAnalysisPrivacyReproductionTest#restrictedOutcomeSurfacesMustNotExposeRecoveredTextOrKey'
```

Tras borrar target/surefire-reports: 1 informe / 2 pruebas / 2 fallos / 0 errores /
0 omitidas / exit 1. Ambos perfiles fallan exclusivamente en
«Outcome.inspectorOutput exposes recovered text». reportText, status, inspectorDetails,
historyInput e historyResult no contienen el marcador recuperado ni la clave inventada.
La comprobación hexadecimal de los bytes del inspector tampoco encuentra la clave.

Causa: analyze devuelve best.plaintext (truncado a 4096 bytes si es necesario) como
inspectorOutput. CipherController transmite ese campo directamente a updateInspector.
No se corrige esa salida: la autorización se limita a los archivos y el paso 3 exige parar.
No se instancian Shelf, historial, visor expandido ni reporter UI en este test: se caracteriza
el resultado que el controlador entrega a esas superficies, sin afirmar una validación UI completa.

La reproducción de archivos quedó verde sin cambiar sus aserciones. El nuevo contrato
del inspector queda rojo intencionalmente. La suite completa no está verde. No se ejecutan
G1/G2/G3 ni las dos fases de refactor tras descubrir este bloqueo. La corrección de archivos
se conserva, pues no es una extracción fallida ni se ha ejecutado una puerta.

## Segunda continuación: resolución del paso 3

Los fallos sobre los bytes de Outcome se retiran: no son una superficie visible.
El presenter real solo pinta byteCount(input/output). La nueva comprobación UI
con shell real pasa para ambos perfiles (2 informes / 7 pruebas / 0 fallos / 0 errores /
0 omitidas / exit 0 junto con la reproducción headless). No se modifica producción.
El informe 76 contiene las líneas del recorrido que justifican la corrección del test.
