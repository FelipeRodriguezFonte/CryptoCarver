# CryptoCarver — guía operativa

CryptoCarver es una aplicación de laboratorio para desarrollar, probar y diagnosticar operaciones criptográficas. No está diseñada para custodiar secretos de producción.

## Arranque

En macOS y Linux, desde la raíz del proyecto:

```bash
./run-modern.sh
```

También se puede ejecutar con Maven (`mvn javafx:run`) o empaquetar con `mvn package`. El JAR resultante es `target/cryptocarver-<version>.jar`, donde la versión procede del `pom.xml`.

Para una entrega verificable (JAR, SBOM CycloneDX, SHA-256 y manifiesto), ejecutar
`bash scripts/build-release.sh`. El checklist completo se encuentra en
`docs/RELEASE_CHECKLIST.md`.

Los paquetes nativos se crean con `./package_macos.sh`, `package_windows.bat` y
`bash package_linux.sh`; los tres usan `jpackage`, que incluye un runtime Java en
el paquete resultante.

## Formatos y conversor

Los selectores globales `Input Format` y `Output Format` determinan la conversión manual normal. No se aplican conversiones implícitas.

Para datos procedentes de mainframe, active `Enable EBCDIC conversion` en **Generic → Manual Conversion** y seleccione el code page. Hay dos sentidos explícitos:

- `Decode EBCDIC → UTF-8`: interpreta los bytes de entrada con el code page EBCDIC y representa los bytes UTF-8 resultantes en el formato global de salida.
- `Encode UTF-8 → EBCDIC`: acepta texto UTF-8 o sus bytes en Hexadecimal, Base64, Binario o Decimal; codifica el texto con el code page seleccionado y representa los bytes EBCDIC en Hexadecimal, Base64, Binario o Decimal.

Por ejemplo, con entrada Hexadecimal `48656C6C6F` (UTF-8 para `Hello`) y `IBM037 — US/Canada`, el modo de codificación produce `C885939396`. El modo de codificación rechaza secuencias UTF-8 malformadas y no permite salida de tipo texto, ya que los bytes EBCDIC no son texto UTF-8.

El modo EBCDIC no se activa automáticamente aunque los bytes parezcan EBCDIC. Así se evita transformar accidentalmente datos UTF-8, binarios o cifrados.
El último code page y sentido seleccionados se recuperan al reiniciar la aplicación.

### Base64URL

En el mismo conversor manual están disponibles `UTF-8 → Base64URL` y `Base64URL → UTF-8`. Usan RFC 4648 sin padding (`=`), el formato esperado por JOSE/JWT/JWE. La decodificación acepta tanto valores con padding como sin él; si el resultado no es UTF-8 válido, informa el error en lugar de sustituir bytes silenciosamente.

## XAdES y TSA

En **XML Security → Sign XML (XAdES)**:

1. Seleccione el XML y el PKCS#12.
2. Cargue y seleccione el alias de firma.
3. Elija el nivel XAdES.
4. Para niveles T, LT y LTA, elija una TSA predefinida o escriba una URL corporativa `http(s)`.

Las opciones predefinidas son DigiCert y FreeTSA. Para validación de confianza en la pestaña de verificación, configure un truststore PKCS#12 o JKS.

## Histórico y secretos

El histórico y las sesiones se guardan en `~/.cryptocarver`. En la primera ejecución se migran automáticamente desde `~/.crypto-calculator` si existe.

El modo laboratorio puede conservar material de clave PQC en los detalles del histórico para repetir y estudiar pruebas. No copie esos ficheros a entornos de producción ni los trate como almacenamiento seguro.

## PKCS#11 y SoftHSM

**SoftHSM** es una implementación de token PKCS#11 por software. Sirve para practicar la integración con un HSM sin disponer de un dispositivo físico; sus claves viven en un almacén local protegido por un PIN, no en un módulo hardware certificado.

Para usar un token real o SoftHSM, abra **Keys → Tools → PKCS#11 Token** y complete la ruta de su biblioteca nativa (`.dylib`, `.so` o `.dll`), el índice del slot y el PIN de usuario. El PIN se entrega al proveedor `SunPKCS11` para abrir la sesión, se borra del campo inmediatamente y no se guarda en perfiles, ajustes ni histórico.

Tras conectar, CryptoCarver muestra únicamente metadatos de objetos. Las claves privadas y secretas siguen siendo manejadores opacos dentro del token. Puede seleccionar una clave secreta en **Symmetric Ciphers** o **Message Authentication Codes** como origen `PKCS#11 Token`; para claves privadas puede firmar y verificar datos hexadecimales en el propio panel PKCS#11.

El mismo panel permite crear un JWS/JWT compacto y un CMS/PKCS#7 SignedData con una clave privada del token y su certificado X.509 asociado. CMS se muestra en Base64 y puede ser encapsulado o detached. Ninguna de estas operaciones convierte la clave privada en PEM ni la escribe en el histórico.

La compatibilidad concreta depende del módulo PKCS#11: el laboratorio cubre AES/DES/3DES, MAC y firmas JCA habituales cuando el token anuncia esos mecanismos. Algoritmos o rellenos no disponibles fallan de forma explícita; nunca se sustituye la operación por una clave exportada.

## Diagnóstico y logs

Abra **Help → Diagnostics** para obtener un informe copiable de runtime. El informe omite deliberadamente claves, contraseñas, entradas y rutas de usuario.

Los componentes que utilizan SLF4J registran advertencias y errores mediante `simplelogger.properties`. El nivel por defecto es `warn`; para una sesión de depuración, edite temporalmente `org.slf4j.simpleLogger.defaultLogLevel=info` y vuelva a compilar.

## Verificación antes de compartir un resultado

- Confirme algoritmo, parámetros, tamaños y formatos en el inspector.
- Revise los detalles de la operación en el histórico.
- Para XAdES, distinga entre integridad válida y confianza de cadena válida.
- Para PQC, pruebe generación, firma/verificación o encapsulación/decapsulación en la misma familia de parámetros.

## Procesos desde la línea de comandos

Ejecute un proceso guardado del Process Designer con:

```bash
java -jar target/cryptocarver-<version>.jar --cli run-process docs/examples/processes/visa-hce.json \
  --set luk.smUdk=94E3194C02105E3B153438D562D5A49D \
  --set msd.atc=0001
```

La forma general es `run-process <fichero.json> [--set nodo.param=valor ...] [--json] [--reveal-secrets]`.
El comando muestra `nodo.output` para cada nodo; las salidas clasificadas como secretas se muestran como `••••` con su longitud. `--reveal-secrets` muestra los valores completos. Los secretos no se guardan en el JSON y deben proporcionarse al ejecutar; evite dejar claves reales en el historial de la terminal. El comando rechaza nodos de escritura de archivos.

Para ejecutar el mismo proceso con varias entradas, use `--batch` con un CSV o JSONL. Cada columna debe llamarse `nodo.parametro` y sustituye ese parámetro en su fila, igual que `--set`. Los `--set` adicionales se aplican a todas las filas. Una columna desconocida invalida la cabecera antes de ejecutar. Los errores de una fila se registran y el resto continúa; la salida incluye el número de fila, las salidas `nodo.output` y un campo de error. El código de salida es 3 si alguna fila falla, o 0 si todas terminan bien. La política de secretos y el rechazo de nodos de escritura de archivos son los mismos que en el modo individual.

```bash
java -jar target/cryptocarver-<version>.jar --cli run-process docs/examples/processes/visa-hce.json \
  --batch docs/examples/processes/visa-hce.csv --format csv --output jsonl
```

La forma general es `run-process <fichero.json> --batch <fichero|-> [--format csv|jsonl] [--output csv|jsonl] [--set nodo.param=valor ...] [--reveal-secrets]`. El guion `-` lee de la entrada estándar. `--reveal-secrets` también muestra valores secretos en la salida del lote; úselo solo cuando proceda.

Hay tres procesos de ejemplo en `docs/examples/processes/`, verificados con vectores conocidos:

- `mc-data-storage.json`: `MC_DS_PARTIAL_KEY`, `MC_DS_DIGEST` y `MC_DS_SUMMARY`; requiere `--set summary.un=11223344`.
- `visa-hce.json`: `VISA_HCE_LUK`, `VISA_HCE_MSD` y `VISA_HCE_QVSDC`; requiere `--set luk.smUdk=94E3194C02105E3B153438D562D5A49D --set msd.atc=0001`.
- `emv-secure-messaging.json`: `EMV_SM_CARD_KEY`, `EMV_SM_SESSION_KEY` y `EMV_SM_MAC`; requiere `--set card.smMk=862F13DF807A13B9D9AEAEC885FE7CA4 --set session.smAc=51DB71A5DCC47F8A --set mac.smAc=51DB71A5DCC47F8A --set mac.atc=0010`.
