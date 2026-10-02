# CipherController, fase 1: análisis de ficheros cifrados

`ui/CipherController.java` tenía 4755 líneas. Esta fase saca el motor de análisis de
ficheros cifrados, la parte más grande y menos ligada a la interfaz.

## Qué entra

- Entrada única: el botón «Analyze Encrypted File» (`cipher.fxml`,
  `#handleAnalyzeFileCipher`). Copia clave, nonce, AAD y tag del panel de ficheros a
  los campos simétricos y llama a `handleAnalyzeEncryptedFile(Path)`.
- Motor: recorrido de codificaciones de entrada × algoritmos según la longitud de la
  clave × modos × rellenos; contenido completo, contenedor de bloques independientes
  (`CFXBI1`) y bloques de tamaño supuesto; evidencia de relleno; calidad del texto
  descifrado; puntuación, confianza y selección de candidatos.
- Salida: carpeta `analysis_<fichero>_<fecha>` junto al fichero con `report.txt`,
  `report.html` y `attempts.csv`; texto en el área de resultado, inspector, histórico
  y estado.
- Lecturas de la interfaz durante el análisis: clave, IV/nonce, AAD (y si su campo
  está deshabilitado) y tag. Se leen al empezar y no cambian durante el análisis,
  que es síncrono en el hilo de JavaFX.

## Código muerto encontrado

`handleSymmetricEncryptFile` y `handleSymmetricDecryptFile` (con `ExpertFileOptions`,
`FileProcessingMode`, `encryptIndependentBlocks`, `encodeFileData` y
`normalizeExpertOptions`) no tienen llamadores: ni FXML, ni otras clases, ni tests.
El cifrado de ficheros real usa `FileCipherOperations` desde `executeFileCipher`.
Se elimina. El análisis sigue reconociendo el contenedor `CFXBI1`.

## Caracterización

`EncryptedFileAnalysisCharacterizationUITest` carga `cipher.fxml`, ejecuta el botón
real con ficheros de prueba (CBC en bruto, CBC en Base64, contenedor `CFXBI1` y GCM con
tag separado) y fija con SHA-256 el texto del resultado, los tres ficheros generados,
el inspector, los mensajes de estado y los errores, tras normalizar rutas y la marca
de tiempo de la carpeta. También cubre la falta de clave y de fichero de tag.

## Fase 3: cifrado simétrico y plantillas

- `SymmetricCipherCoordinator`: cifrar/descifrar del área compartida (bloque con modo y
  relleno, ChaCha20, Salsa20, GCM, ChaCha20-Poly1305 y XChaCha20-Poly1305), resultado AEAD
  con texto cifrado y tag por separado, paquete de Shelf del último resultado AEAD y aviso
  de nonce repetido.
- `CipherTemplateCoordinator`: plantillas integradas, personales y «Reset Defaults».
- Siguen en el controlador la visibilidad de campos por modo, las insignias, la selección de
  claves de Key Lab y FPE.

`SymmetricCipherCharacterizationUITest` fijó el comportamiento antes de mover código y
destapó tres fallos, corregidos después:

- Salsa20 no funcionaba: pasaba por el cifrado genérico con modo «None» y exigía un IV de
  16 bytes. Ahora usa su motor con nonce de 8 bytes. Con claves de Key Lab da un error claro,
  porque el laboratorio no ofrece Salsa20.
- El texto de ayuda del nonce decía 8 bytes para ChaCha20; ahora muestra la longitud
  recomendada de cada algoritmo (12 para ChaCha20).
- Descifrar sin clave mostraba «Invalid Hexadecimal Format»; ahora dice que falta la clave,
  igual que al cifrar.
