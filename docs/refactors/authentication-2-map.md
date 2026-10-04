# Authentication: mapa de fase 2

Base de la tanda: main en 7aa3519b2eb77ba4edf9604435d3f21365053b2d. Rama: codex/authentication-1. La fase 1 dejó AuthenticationController.java en 1163 líneas.

## Límites verificados

Conteo sobre el archivo actual después de la fase 1, contando las líneas de la declaración a la llave de cierre:

| Método | Líneas | Conteo |
|---|---:|---:|
| handleGenerateMAC() | 960–1020 | 61 |
| handleVerifyMAC() | 1025–1100 | 76 |
| getHsmMacKeyId() | 921–934 | 14 |
| isPkcs11MacSource() | 936–938 | 3 |
| getPkcs11MacKeyAlias() | 940–947 | 8 |
| getManualMacKey() | 949–955 | 7 |
| getTruncationBytes() | 1105–1119 | 15 |
| generateMac(...) | 1121–1150 | 30 |

El límite previsto de AuthenticationMacCoordinator comprende los dos handlers y sus helpers privados, incluido el dispatch a HMAC/CMAC/GMAC/Poly1305 y a los proveedores HSM/PKCS#11. Los callbacks de selección visual y el código de inicialización/visibilidad de esos controles siguen en el controlador. No se cambia la API pública.

## Estado vivo leído y escrito

| Estado o servicio | Lectura | Escritura | Observación |
|---|---|---|---|
| authMacAlgorithmCombo | Ambos handlers eligen algoritmo | Ninguna | null genera un error de validación. |
| macKeySourceCombo, macHsmKeyCombo | Los helpers distinguen entrada manual, Simulated HSM y PKCS#11; consultan selección/alias y metadatos Key Lab | Ninguna en los handlers | Resolver la clave a la hora del evento mantiene selección actualizada. Los eventos de cambio/visibilidad permanecen como cableado de UI. |
| authMacKeyField | getManualMacKey() lee hexadecimal y decodifica bytes | Ninguna | El byte array es secreto y no debe agregarse a detalles, logs ni transcripción. La longitud/algoritmo debe validarse por el proveedor crypto. |
| authMacNonceField | generateMac() lo lee para GMAC-AES | Ninguna | Requerido para GMAC; al ser material de autenticación, la transcripción no lo registra. |
| authMacTruncationCombo | getTruncationBytes() analiza el valor actual | Ninguna | El parseo es lógica pura. |
| authMacVerifyField | handleVerifyMAC() parsea el MAC hexadecimal o Base64 | Ninguna | Un MAC incorrecto publica verificación inválida sin incluir el secreto de clave. |
| authInputArea, inputFormatCombo | Ambos handlers llaman AuthenticationDataFormatter.read(...) | Ninguna | El valor/formato vigente debe obtenerse por proveedores, no congelarse al construir el coordinador. |
| authOutputArea, outputFormatCombo | Ninguna directa | El handler de generación escribe el MAC formateado | Cableado visual mediante AuthenticationDataFormatter.write(...). |
| mainController (StatusReporter) | Preflight y validaciones | Errores, estado/avisos y publicación de resultado | Obtenerlo con Supplier<StatusReporter> perezoso; en los tests también se conserva el modo no modal. |
| LOG | Ninguna | Registra excepciones de generación/verificación | Comprobar que los errores de proveedor no incluyen la clave manual ni el MAC de prueba. |
| Key Lab / Simulated HSM / PKCS#11 | Lee metadata, bytes y sesión según la fuente elegida | Ninguna de los handlers escribe en Key Lab | Las selecciones se resuelven en el momento de la operación. El test de caracterización usa clave manual inventada y no modifica el Key Lab del usuario. |
| AppSettings / visibilidad | No se lee aquí | Resultado consumido aguas abajo | FULL_LAB, MASKED y REDACTED gobiernan las salidas comunes. |
| Historial, Shelf, inspector, barra, visor expandido y telemetría | Ninguna directa | La publicación hace capturable output y details | Deben observarse con los tres perfiles. No se deben exponer ni la clave MAC ni el valor MAC secreto bajo MASKED/REDACTED. |

## Lógica de operación frente a cableado

- Parsear/validar la clave manual, resolver la fuente elegida, validar nonce y truncado, producir MAC, parsear el valor pegado y comparar con tiempo constante son lógica del coordinador.
- Obtener texto/formato de los controles, mostrar validaciones/preflight, mostrar el MAC generado y publicar OperationResult son dependencias de UI/servicios que entran por un record View de Supplier y el Supplier<StatusReporter>.
- MACOperations, SharedMaterialParser, SimulatedHsmProvider y Pkcs11SessionManager siguen siendo las dependencias existentes. No se toca crypto/.
- Defecto de confidencialidad reproducido y corregido antes de extraer: generación duplicaba el MAC en el detalle Output y tanto generación como verificación publicaban los bytes sin clasificación. La prueba previa observó fugas en perfiles restringidos; el arreglo elimina el detalle y clasifica ambos outputs como SECRET. El detalle de reproducción y el digest están en authentication-2-characterization-failures.md.
- Los handlers son síncronos y consumen el estado actual del formulario dentro de una única acción. El límite es separable pasando proveedores vivos de controles; no requiere callback al controlador ni altera el orden de eventos. Si la caracterización contradice este análisis, no se fuerza la extracción.

## Caracterización

AuthenticationMacCharacterizationUITest cubre generación/verificación completa con clave inventada, MAC alterado, clave ausente, clave de longitud incorrecta y mensajes legibles en EN/ES. Observa FULL_LAB, MASKED y REDACTED, incluyendo historial, Shelf, barra de estado, visor expandido, inspector y logs; el test aísla/restaura AppSettings, Shelf e historial. El ensayo defectuoso se anotó antes del arreglo y el transcript protegido quedó fijado después.

## Extracción aplicada

AuthenticationMacCoordinator recibe un record View de proveedores para algoritmos, material/fuente de clave, nonce, truncado, controles de entrada/salida y formatos. El getter perezoso captura referencias directas a controles y Supplier<StatusReporter>; no captura AuthenticationController. Los dos handlers públicos son delegados de una línea. La comprobación de fuente PKCS#11 que usa refreshHsmKeys quedó inline en el controlador de UI y el coordinador conserva su propio helper para resolver la clave al operar.

InlineErrorBannerTest ajusta un único fixture: antes mutaba por reflexión mainController, lo que no actualiza el Supplier del coordinador; ahora llama al init público con el reporter de prueba y conserva los formatos cargados. La suite normal detectó el desajuste porque quedó visible el banner de preflight anterior, no porque cambiara la validación de producción.

El SHA-256 del transcript protegido, verificado antes y después de la extracción, es 95e2692fb07d42fca1f02df27fda47240d1cdb0120120ed8e153031880dad598. AuthenticationController pasa de 1163 a 955 líneas (−208).

## Puerta de control

Las ejecuciones completas finales pasaron:

| Comando | XML frescos | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|---:|
| mvn -o -q test -Plow-cpu | 421 | 2863 | 0 | 0 | 1 |
| mvn -o -q test -Plow-cpu -DrunUiTests=true | 106 | 519 | 0 | 0 | 0 |

Los recuentos son distintos; los XML UI se contaron solo entre los actualizados por la segunda ejecución, sin sumar informes normales antiguos. ExpandedViewerLifecycleUITest pasó. Hubo un fallo inicial en InlineErrorBannerTest porque ese fixture sustituía solo el campo antiguo del reporter; el mapa documenta el ajuste del fixture a init(...), y la repetición de ambas suites quedó limpia.
