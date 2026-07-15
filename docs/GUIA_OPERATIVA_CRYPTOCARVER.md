# CryptoCarver — guía operativa

CryptoCarver es una aplicación de laboratorio para desarrollar, probar y diagnosticar operaciones criptográficas. No está diseñada para custodiar secretos de producción.

## Arranque

En macOS y Linux, desde la raíz del proyecto:

```bash
./run-modern.sh
```

También se puede ejecutar con Maven (`mvn javafx:run`) o empaquetar con `mvn package`. El JAR resultante es `target/cryptocarver-2.3.0.jar`.

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

## Diagnóstico y logs

Abra **Help → Diagnostics** para obtener un informe copiable de runtime. El informe omite deliberadamente claves, contraseñas, entradas y rutas de usuario.

Los componentes que utilizan SLF4J registran advertencias y errores mediante `simplelogger.properties`. El nivel por defecto es `warn`; para una sesión de depuración, edite temporalmente `org.slf4j.simpleLogger.defaultLogLevel=info` y vuelva a compilar.

## Verificación antes de compartir un resultado

- Confirme algoritmo, parámetros, tamaños y formatos en el inspector.
- Revise los detalles de la operación en el histórico.
- Para XAdES, distinga entre integridad válida y confianza de cadena válida.
- Para PQC, pruebe generación, firma/verificación o encapsulación/decapsulación en la misma familia de parámetros.
