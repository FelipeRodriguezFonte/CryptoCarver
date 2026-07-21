# Inicio rápido de CryptoCarver

CryptoCarver es un laboratorio de criptografía de escritorio. Esta guía propone
recorridos cortos y repetibles; trabaja siempre con datos de prueba.

## 1. Arranque

Desde el árbol de fuentes:

```bash
./run-modern.sh
```

También se puede abrir el JAR generado con `java -jar target/cryptocarver-<version>.jar`.
Para consultar la versión sin iniciar la interfaz: `java -jar target/cryptocarver-<version>.jar --version`.
El diálogo **Help → About** y **Help → Diagnostics** indican además si el build es
de canal `laboratory`, `stable` o `experimental`.

## 2. Convertir y comprobar bytes

1. Abre **Generic → Manual Conversion**.
2. Indica el formato de entrada y de salida antes de convertir. Por ejemplo,
   convierte `486F6C61` desde Hexadecimal a UTF-8 para obtener `Hola`.
3. Para Base64URL usa `SG9sYQ` como representación sin padding: la herramienta
   valida el formato de forma estricta.
4. Si sospechas que el contenido procede de un host, usa **Byte Inspector** para
   ver hexadecimal, ASCII y candidatos de charset antes de convertir.

### Hexadecimal comprimido de host

En **Generic → Compressed Hex (2-row)**, pega las dos filas que muestra el host:

```text
AFD12
CA123
```

Pulsa **Two rows → Hex**: el resultado será `ACFAD11223`. La operación inversa
divide un hexadecimal de longitud par en las dos filas intercaladas. Se rechazan
filas de longitudes distintas o caracteres no hexadecimales.

## 3. Generar y revisar claves

1. En **Keys → Symmetric → Key Generation**, selecciona DES, TDES o AES para
   generar material de laboratorio y, si procede, su KCV.
2. En **Keys → Asymmetric**, genera RSA, ECDSA, DSA o EdDSA. Los pares aparecen
   en pestañas pública/privada.
3. Para analizar una clave ya existente usa **Tools → Key Material Inspector**.
4. El menú **Simulated HSM** sirve para experimentar con claves de sesión no
   exportables; no representa un HSM certificado ni persistente.

El botón **Expand Result** abre el resultado de la operación actual en una ventana
independiente. Desde ella puedes buscar, copiar, ajustar el ajuste de línea o
guardar una instantánea. **Maximize** aprovecha toda la pantalla; `⌘/Ctrl+F` abre
la búsqueda, `⌘/Ctrl+S` guarda y `Esc` cierra el visor. Para un par asimétrico
muestra conjuntamente ambas partes.

## 4. Firmar XML y usar TSA

1. Ve a **XML Security → Sign XML (XAdES)** y selecciona el XML, la clave y el
   certificado de laboratorio.
2. En la sección TSA elige uno de los perfiles de prueba o escribe una URL propia.
   Las credenciales Basic/Bearer sólo viven en memoria durante la sesión.
3. Usa **Test TSA** antes de firmar y **Validate Token** para revisar CMS, imprint,
   EKU `timeStamping` y, opcionalmente, la cadena contra un truststore.
4. Tras validar XAdES, guarda los informes sólo si puedes custodiar los datos de
   certificados que contienen.

## 5. Post-cuántica

La sección **Post-Quantum** presenta los nombres normalizados del NIST:
ML-KEM, ML-DSA y SLH-DSA. Prueba primero la demostración KEM Alice/Bob; el secreto
de Alice sólo se muestra después de desencapsular el ciphertext de Bob. El benchmark
es cancelable y sus resultados son comparativos de laboratorio, no mediciones de
capacidad de producción.

## 6. Pagos y perfiles de laboratorio

El menú **Laboratory** carga datos de ejemplo para DUKPT, PIN, EMV, TR-31 y Secure
Messaging. **Load Data** sólo rellena campos; **Run and Verify** debe pulsarse
explícitamente y contrasta el resultado con el vector esperado. Estos vectores no
sustituyen pruebas de certificación de redes de pago.

## 7. Histórico, recetas y exportación

- **Recent Operations** conserva detalles de operaciones y permite compararlas.
- Selecciona la política de secretos antes de exportar una receta: `REDACTED`
  elimina detalles secretos, `MASKED` los oculta parcialmente y `FULL_LAB` los
  conserva para reproducción local.
- **Export Report** genera un Markdown legible de la operación seleccionada y
  aplica la misma política de secretos. Úsalo para documentación o soporte;
  revisa siempre el contenido antes de compartirlo.
- Las recetas con varios pasos se validan antes de importarse y se ejecutan como
  una cadena; una receta no debe contener secretos de producción.

## Siguiente lectura

- [Guía operativa](GUIA_OPERATIVA_CRYPTOCARVER.md): formatos y operaciones.
- [Catálogo de formatos y charsets](FORMATS_AND_CHARSETS.md): sintaxis y code pages.
- [Límites de laboratorio](LAB_VS_PRODUCTION.md): qué no asume CryptoCarver.
- [Checklist de release](RELEASE_CHECKLIST.md): cómo generar una entrega trazable.
