# Authentication: mapa de fase 1

Base: `main` en `7aa3519b2eb77ba4edf9604435d3f21365053b2d`. Rama: `codex/authentication-1`. `AuthenticationController.java` tiene 1359 líneas en la base.

## Límites verificados

Un conteo de cuerpos Java balanceando llaves, sin contar comentarios ni literales, confirma:

| Método | Líneas | Conteo |
|---|---:|---:|
| `handleSign()` | 697–767 | 71 |
| `handleVerify()` | 772–848 | 77 |
| `handleGenerateMAC()` | 1080–1140 | 61 |
| `handleVerifyMAC()` | 1145–1220 | 76 |

Esta fase solo mueve los dos handlers de firma. Los handlers MAC quedan para fase 2.

## Estado vivo leído y escrito

| Estado | Lectura | Escritura | Observación |
|---|---|---|---|
| `signatureAlgorithmCombo` | Ambos handlers resuelven el algoritmo seleccionado | Ninguna | Si falta, informan error y terminan. |
| `signaturePrivateKeyArea`, `currentPrivateKey` | `handleSign` vuelve a parsear PEM pegado; si no hay PEM reciente usa la clave cargada en memoria | `handleSign` actualiza `currentPrivateKey` al parsear texto | La clave cargada se comparte con los handlers de carga/generación de la fase 3. No debe copiarse a resultados ni a transcripciones. |
| `signaturePublicKeyArea`, `currentPublicKey` | `handleVerify` vuelve a parsear PEM pegado; de lo contrario usa la clave cargada | `handleVerify` actualiza `currentPublicKey` al parsear texto | Comparte el mismo estado con `loadPublicKey` de fase 3. |
| `authInputArea`, formatos de entrada | Ambos handlers llaman `getInputDataAsBytes()` | Ninguna | El helper convierte según el formato común del shell. El proveedor debe resolverse al invocar, no capturar el controlador. |
| `authOutputArea`, formato de salida | Ninguna directa | `handleSign` llama `setOutputData(signature)` | La conversión y la escritura visual son cableado de UI y siguen como callbacks. |
| `signatureVerifyField` | `handleVerify` lee y decodifica la firma | Ninguna | Parsing de entrada con formato Hex/Base64; error asociado al campo. |
| `mainController` (`StatusReporter`) | Preflight readiness | Errores, avisos de éxito e `OperationResult.publish` | El coordinador debe obtenerlo con `Supplier<StatusReporter>`; se resuelve de forma perezosa. |
| `LOG` | Ninguna | Registra excepciones de operación | Mantener el mismo contexto de error sin capturar `AuthenticationController`. |
| `AppSettings` / perfil de visibilidad | No se lee en los handlers | Los handlers publican resultados que consumen inspector, barra, historial, visor expandido y telemetría | La política vive aguas abajo. La caracterización verifica los tres perfiles para descubrir si la clasificación publicada es suficiente. |
| Key Lab / Shelf | No se consultan directamente | Ninguna | Fuera del alcance de firma; los resultados pueden aparecer en superficies comunes de captura. |

## Lógica de operación frente a cableado

- Parsear PEM pegado, validar clave ausente/tipo, decodificar firma, firmar/verificar, construir metadatos no secretos y decidir el resultado son lógica del coordinador de firmas.
- Leer datos con el formato activo, escribir bytes con el formato de salida, mostrar errores/avisos, consultar preflight y publicar por el shell son efectos de UI/servicios y deben entrar por `View`, callbacks y `Supplier<StatusReporter>`.
- `SignatureOperations` y `SharedMaterialParser` son dependencias de dominio ya existentes; no se cambia `crypto/`.
- Riesgo de seguridad que debe comprobar la caracterización: `.output(signature)` usa la clasificación predeterminada `PUBLIC`. Las firmas se han incluido expresamente en los datos que MASKED y REDACTED deben ocultar; el resultado real de inspector, historial, Shelf, barra, visor expandido y telemetría decidirá si se registra un defecto antes de corregirlo.
- El `View` se creará de manera perezosa por el controlador y no se almacenará en un campo del coordinador. `handleSign()` y `handleVerify()` públicos quedarán como delegados de una línea y conservarán sus firmas.

## Cobertura de partida y huecos

`XMLSignatureControllerUITest`, pruebas de operaciones de firma, `ResultCaptureCharacterizationUITest`, `ResultViewerSecurityCharacterizationTest` y `ResultCapturePinSecurityUITest` cubren partes de crypto y de las superficies comunes, pero no fijan en una sola ejecución el ciclo de firma/verificación, errores EN/ES ni los tres perfiles para los bytes de firma. La nueva `AuthenticationSignatureCharacterizationUITest` añadirá esa transcripción con claves inventadas, observará el shell de producción y comprobará que el material sensible no se escribe en historial, Shelf, barra, visor expandido ni telemetría bajo MASKED/REDACTED.

La prueba aislará y restaurará `AppSettings`, el Shelf y el historial. Los fallos observados se guardarán en `authentication-1-characterization-failures.md` antes de cualquier arreglo de producto; un arreglo tendrá su commit propio.

## Decisión de separabilidad

La ejecución de firma y verificación forma una unidad separable del resto del controlador: solo comparte las claves cacheadas y los helpers de formato/entrada, que se exponen mediante getters y callbacks de la vista. La fase 3 podrá cambiar cómo se cargan esas claves sin que el coordinador de firma capture el controlador. Si la transcripción muestra orden observable adicional, el mapa se actualizará antes de extraer.
