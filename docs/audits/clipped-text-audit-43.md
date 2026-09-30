# Encargo 43: auditoría de textos recortados

Rama: `luna/clipped-text-audit`, desde `main` (`be9f7e8`). Sin push. Implementación y revisión realizadas directamente en Sol por petición del usuario. No se hizo prueba manual.

## Método y reproducción

La herramienta carga `main-view-modern.fxml` mediante `Fxml.loader`, navega a cada destino distinto del registro, aplica `styles.css` y el tema solicitado, y muestra una escena de 1400 × 900 (tamaño de diseño de la aplicación). Cada escena se cierra y desconecta antes de pasar a la siguiente; se permite que JavaFX procese su limpieza. Antes de medir se drenan las actualizaciones diferidas del shell. Cada pantalla usa divisores 0,22/0,78, escala 1 y densidad normal; se restauran divisores, rutas, escala, densidad y visibilidad al cerrarla, evitando que el guardado automático de layout contamine las pantallas posteriores. Cada combinación de idioma/tema se ejecuta en un JVM independiente. Se utilizó `low-cpu` después de observar saturación de CPU en la primera exploración.

Las rutas alias del mismo `Route` se cuentan una vez. El nombre representativo se elige en orden alfabético. Se examinan también las pestañas alternativas visibles, una a una, restaurando la selección original; no se explora el producto cartesiano de estados. Se añaden Process Designer con inspector oculto, Workbench con cabeceras de almacén (sin registros de datos) y el diálogo informativo de producción. Los recuentos son **hallazgos por estado**: un mismo rótulo puede aparecer en varios estados.

Un hallazgo compara el texto completo de un `Labeled` con su `LabeledText` del skin. Se verifica que el texto pertenece al control, evitando confundir el contenido de un `TitledPane` con su cabecera. Se normalizan únicamente los marcadores de mnemónicos. Los `MenuButton` delegan su rótulo a un `MenuLabeledImpl` del skin; ese `Labeled` también se inspecciona y queda identificado por la ruta de su botón padre. Se registran ruta del nodo, id, clase, clases CSS, textos completo/visible, ancho del control, ancho preferido y tipo del padre. Las cabeceras de pestañas y columnas se inspeccionan a través de sus `Label` de skin. Una cabecera sin esa representación queda excluida con motivo. No se mide recorte geométrico de glifos ni se garantiza cobertura de popups cerrados o estados que requieran ejecutar operaciones.

```sh
# Una sola ejecución Maven simultánea. Antes de cada pasada, apartar
# target/test-home para obtener un user.home limpio (Surefire lo configura).
mvn -o -q -Plow-cpu test -Dtest=ClippedTextAuditTool \
  -DclippedTextAuditOut=target/clipped-text-audit \
  -DclippedTextAuditLocale=en -DclippedTextAuditTheme=light
# Repetir con en/dark, es/light y es/dark.
```

La salida determinista queda en `<directorio>/<idioma>-<tema>.txt`, con resumen por pantalla, total, hallazgos ordenados y exclusiones individuales con motivo. La propiedad opt-in mantiene la auditoría completa fuera de la suite normal. Los informes completos de esta ejecución están en `target/clipped-text-audit/baseline/` y `target/clipped-text-audit/final/`; la tabla siguiente preserva sus recuentos en Git. Para reproducir la línea base, usar el commit de medición anterior a los arreglos.

## Exclusiones y límites

- Subárboles `visible=false` y contenido de `TitledPane` plegado: no se muestran al usuario.
- Rótulos no gestionados: fuera del layout solicitado.
- `Cell` y sus descendientes: contenido de datos de listas, árboles, tablas y selecciones; no se registran sus valores.
- Valores de salida del Workbench (`lblFormat`, `lblAlgorithm`, `lblHasPrivate`, `lblSubject`, `lblKeySize`, `lblFingerprint`, `lblValidity`): no son rótulos. Su texto no se lee. Esta exclusión no reduce la línea base: se verificó que los cuatro informes tenían cero hallazgos en esos ids.
- Rótulos sin `LabeledText` propio fiable: no se interpreta el texto de otro control como si fuera el suyo.
- Estar fuera del viewport de un `ScrollPane` no causa por sí mismo un hallazgo. Si el skin realmente trunca el texto, se cuenta, aunque haya que desplazar la pantalla para verlo.
- No hay exclusiones por iconos ni por `textOverrun=CLIP`: no se ha identificado ningún recorte decorativo intencionado que justifique ignorarlo.
- No se leen campos de entrada. Se usan perfiles de prueba limpios y solo estados de interfaz sin datos de usuario. Los nombres comerciales de rutas heredadas se sustituyen por `[legacy]` en los informes.

## Línea base

Las pasadas de calibración se sustituyeron después de corregir la inicialización diferida y el guardado automático de divisores. La línea base definitiva, estabilizada y aislada antes de los arreglos, es **765/765/862/862** (commit `8086590` de la herramienta; medición en el commit siguiente).

| Pantalla / estado | en claro | en oscuro | es claro | es oscuro |
|---|---:|---:|---:|---:|
| AES Encryption | 0 | 0 | 0 | 0 |
| AES Key Wrap | 0 | 0 | 0 | 0 |
| AKB | 1 | 1 | 1 | 1 |
| ARPC Generation | 0 | 0 | 1 | 1 |
| ARQC Generation | 0 | 0 | 0 | 0 |
| ASN.1 | 0 | 0 | 0 | 0 |
| ASN.1 / tabs 0:1 | 0 | 0 | 0 | 0 |
| ASN.1 Decoder | 0 | 0 | 0 | 0 |
| ASN.1 Decoder / tabs 0:1 | 0 | 0 | 0 | 0 |
| ASN.1 Encode | 0 | 0 | 0 | 0 |
| ASN.1 Encode / tabs 0:0 | 0 | 0 | 0 | 0 |
| ASiC | 0 | 0 | 0 | 0 |
| AdES Validation | 0 | 0 | 0 | 0 |
| Add UsernameToken (WSS) | 0 | 0 | 0 | 0 |
| Asymmetric Ciphers | 0 | 0 | 0 | 0 |
| Batch Runner | 4 | 4 | 4 | 4 |
| CMS Decrypt | 0 | 0 | 0 | 0 |
| CMS Decrypt / tabs 0:1 | 0 | 0 | 0 | 0 |
| CMS Inspector | 0 | 0 | 0 | 0 |
| COSE Decrypt0 | 0 | 0 | 0 | 0 |
| CVV Generation | 0 | 0 | 0 | 0 |
| Certificate Chain | 0 | 0 | 0 | 0 |
| Check Digits | 0 | 0 | 0 | 0 |
| Clear PIN Blocks | 0 | 0 | 0 | 0 |
| Clipboard Shelf | 0 | 0 | 0 | 0 |
| Combine Components | 0 | 0 | 0 | 0 |
| Combine Components / tabs 0:1 | 0 | 0 | 0 | 0 |
| Compare Certificates | 0 | 0 | 0 | 0 |
| Compare Public / Private Key | 0 | 0 | 0 | 0 |
| Compressed Hex (2-row) | 0 | 0 | 0 | 0 |
| Crypto Envelope Inspector | 0 | 0 | 0 | 0 |
| DSA Key Generation | 0 | 0 | 0 | 0 |
| DSA Key Generation / tabs 0:1 | 0 | 0 | 0 | 0 |
| DUKPT TDES | 0 | 0 | 1 | 1 |
| Decode EBCDIC | 0 | 0 | 0 | 0 |
| Decrypt JWE | 0 | 0 | 0 | 0 |
| Decrypt SOAP Body (WSS) | 0 | 0 | 1 | 1 |
| Derive Key | 1 | 1 | 1 | 1 |
| Digital Signatures | 0 | 0 | 0 | 0 |
| Digital Signatures / tabs 0:1 | 0 | 0 | 0 | 0 |
| Dilithium (ML-DSA) | 0 | 0 | 0 | 0 |
| Dilithium (ML-DSA) / tabs 0:1 | 0 | 0 | 0 | 0 |
| Dilithium (ML-DSA) / tabs 0:2 | 0 | 0 | 0 | 0 |
| ECDSA Key Generation | 0 | 0 | 0 | 0 |
| ECDSA Key Generation / tabs 0:1 | 0 | 0 | 0 | 0 |
| EMV Offline Data Authentication | 1 | 1 | 5 | 5 |
| EMV Operations | 0 | 0 | 1 | 1 |
| EMV TLV Inspector | 0 | 0 | 0 | 0 |
| EdDSA Key Generation | 0 | 0 | 0 | 0 |
| EdDSA Key Generation / tabs 0:1 | 0 | 0 | 0 | 0 |
| Encoding/Conversion | 0 | 0 | 2 | 2 |
| Encrypt SOAP Body (WSS) | 0 | 0 | 0 | 0 |
| Encrypted PIN Block Decoded | 0 | 0 | 0 | 0 |
| Epoch Converter | 0 | 0 | 0 | 0 |
| Export History | 0 | 0 | 4 | 4 |
| File Cipher (Streaming) | 0 | 0 | 0 | 0 |
| Format-Preserving Encryption | 0 | 0 | 0 | 0 |
| GPG | 0 | 0 | 0 | 0 |
| Generate Certificate | 0 | 0 | 3 | 3 |
| Generate MAC | 0 | 0 | 0 | 0 |
| Generate PIN | 0 | 0 | 0 | 0 |
| Generate RSA Key | 0 | 0 | 0 | 0 |
| Generate RSA Key / tabs 0:1 | 0 | 0 | 0 | 0 |
| Generate Symmetric Key | 0 | 0 | 0 | 0 |
| Generate UUID | 0 | 0 | 0 | 0 |
| Hashing | 0 | 0 | 0 | 0 |
| ICSF / CCA Batch Analysis | 4 | 4 | 5 | 5 |
| ICSF / CCA Batch Analysis / tabs 0:1 | 4 | 4 | 5 | 5 |
| ICSF / CCA Batch Analysis / tabs 0:2 | 4 | 4 | 5 | 5 |
| ICSF / CCA Batch Analysis / tabs 0:3 | 5 | 5 | 6 | 6 |
| ICSF / CCA Key Export / Import | 2 | 2 | 2 | 2 |
| ICSF / CCA Key Export / Import / tabs 0:1 | 2 | 2 | 2 | 2 |
| ICSF / CCA Key Export / Import / tabs 0:2 | 3 | 3 | 3 | 3 |
| ICSF / CCA Key Export / Import / tabs 0:3 | 3 | 3 | 3 | 3 |
| ICSF / CCA Key Token Analyzer | 3 | 3 | 3 | 3 |
| ISO 8583 | 0 | 0 | 0 | 0 |
| Information dialog | 0 | 0 | 0 | 0 |
| Inspect Signed XML | 0 | 0 | 0 | 0 |
| Issue Certificate from CSR | 0 | 0 | 0 | 0 |
| JSON Formatter | 0 | 0 | 0 | 0 |
| Key & Certificate Format Workbench | 0 | 0 | 0 | 0 |
| Key & Certificate Format Workbench / store headers | 0 | 0 | 0 | 0 |
| Key Lab | 0 | 0 | 1 | 1 |
| Key Material Inspector | 0 | 0 | 0 | 0 |
| KeyStore Inspector | 0 | 0 | 0 | 0 |
| ML-KEM Decapsulate | 0 | 0 | 0 | 0 |
| Modular Arithmetic | 0 | 0 | 0 | 0 |
| PAdES | 0 | 0 | 0 | 0 |
| PKCS#11 Profiles | 0 | 0 | 0 | 0 |
| PKCS#11 Token | 1 | 1 | 1 | 1 |
| PQC Sign | 0 | 0 | 0 | 0 |
| Parse Certificate | 0 | 0 | 0 | 0 |
| Process Designer | 361 | 361 | 392 | 392 |
| Process Designer / inspector hidden | 361 | 361 | 392 | 392 |
| RFC 3161 Timestamp | 0 | 0 | 1 | 1 |
| RSA Key Exchange | 0 | 0 | 0 | 0 |
| RSA Key Exchange / tabs 0:1 | 0 | 0 | 0 | 0 |
| Random Generation | 0 | 0 | 0 | 0 |
| Recent Operations | 0 | 0 | 4 | 4 |
| Save XAdES XML | 1 | 1 | 3 | 3 |
| Saved Sessions | 0 | 0 | 0 | 0 |
| Sign SOAP (WSS) | 0 | 0 | 5 | 5 |
| TR-31 Export | 0 | 0 | 0 | 0 |
| TR-31 Export / tabs 0:1 | 0 | 0 | 0 | 0 |
| TR-34 Key Distribution | 1 | 1 | 1 | 1 |
| TR-34 Key Distribution / tabs 0:1 | 1 | 1 | 1 | 1 |
| Track 2 Decoding | 0 | 0 | 0 | 0 |
| Track 2 Decoding / tabs 0:1 | 0 | 0 | 0 | 0 |
| Validate Certificate | 0 | 0 | 0 | 0 |
| Validate Symmetric Key | 0 | 0 | 0 | 0 |
| Verify SOAP (WSS) | 0 | 0 | 0 | 0 |
| Verify UsernameToken (WSS) | 0 | 0 | 1 | 1 |
| Verify XML (XAdES) | 0 | 0 | 0 | 0 |
| [legacy] Host Command Bank | 0 | 0 | 0 | 0 |
| [legacy] Key Block | 1 | 1 | 1 | 1 |
| [legacy] Variant LMK | 1 | 1 | 1 | 1 |
| TOTAL | 765 | 765 | 862 | 862 |

## Arreglos por grupo

Las pasadas de vigilancia de cada grupo recorrieron **todo el registro en español y tema claro**. En ninguna aumentó el recuento de otra pantalla. La verificación final cubrió de nuevo las cuatro combinaciones.

| Grupo | Técnicas | Hallazgos del grupo antes → después | Total es claro antes → después |
|---|---|---:|---:|
| Process Designer y cabecera común usada por Workbench | 1 y 2: mínimo de icono, altura natural de paleta, títulos ajustables, botones de cabecera con tamaño natural | 784 → 0; cabecera en arranque corto 8 → 0 | 862 → 78 |
| Claves, CCA y generación de certificados | 2: explicaciones conservan altura natural y rótulos de columnas estrechas admiten varias líneas | 45 → 0 | 78 → 33 |
| Conversión de archivos, Batch Runner e historial | 1 y 2: ancho natural de botones/rótulos y aviso de visibilidad ajustable | 14 → 0 | 33 → 19 |
| Formularios XML, SOAP y EMV; botón de derivación | 1 y 2: rótulos ajustables, casillas con ancho natural y cota máxima del botón según su texto | 19 → 0 | 19 → 0 |

El Workbench no presentó recortes en la pasada completa aislada. La regresión corta al abrirlo después de Process Designer reveló ocho recortes de la cabecera del shell; se corrigieron también, sin excluirlos ni alterar la prueba. No se cambiaron fuentes, colores, traducciones, estructuras de contenedores ni código criptográfico. Los cambios de producción son exclusivamente atributos de tamaño/ajuste de texto en FXML y las reglas finales de paleta en «Legibility overrides».

En FXML se usa `-Infinity`, el valor numérico de `Region.USE_PREF_SIZE`: el literal `USE_PREF_SIZE` no se puede convertir directamente a `double`. Las propiedades CSS `-fx-pref-width`/`-fx-pref-height` son búsquedas de valores, no una constante de tamaño natural si no existe un valor CSS; por ello los tamaños naturales se expresan en FXML y el mínimo de icono se expresa en píxeles.

## Recuento final

| Combinación | Línea base | Final |
|---|---:|---:|
| Inglés claro | 765 | **0** |
| Inglés oscuro | 765 | **0** |
| Español claro | 862 | **0** |
| Español oscuro | 862 | **0** |

No quedan hallazgos pendientes ni recortes justificados dentro del alcance medido. Los recuentos por pantalla se conservan en [clipped-text-final.tsv](clipped-text-final.tsv). Español claro reutiliza la pasada completa del último grupo, realizada sobre el mismo código final; las otras tres combinaciones se volvieron a ejecutar en JVM independientes.

## Instantáneas de estilo

Se amplió `ComputedStyleSnapshotTool` para registrar `minW`, `prefW`, `maxW`, `wrap` y `overrun`, además de sus propiedades previas. Las capturas antes/después se hicieron con `user.home` limpio, una pantalla y un tema por invocación (Process Designer y Workbench, tema claro). La herramienta cierra y desconecta su escena y restaura la última ruta.

```sh
mvn -o -q -Plow-cpu test -Dtest=ComputedStyleSnapshotTool \
  '-DstyleSnapshotRoute=Process Designer' \
  -DstyleSnapshotTheme=theme-light.css \
  -DstyleSnapshotOut=target/clipped-text-audit/before-process.txt
# Repetir después y para Key & Certificate Format Workbench.
diff -u target/clipped-text-audit/before-process.txt \
  target/clipped-text-audit/after-process.txt
```

Comparación estricta por clave de nodo:

| Captura (tema claro) | Nodos antes/después | Pares de líneas cambiados | Líneas −/+ del diff | Diferencias ajenas a tamaño/ajuste |
|---|---:|---:|---:|---:|
| Process Designer | 2007 / 2007 | 407 | 814 | 0 |
| Workbench | 441 / 441 | 3 | 6 | 0 |

No se añadió ni eliminó ninguna clave de nodo. Al retirar únicamente los campos de tamaño y ajuste, cada línea antes/después resulta idéntica: fondos, bordes, padding, clases, colores, fuentes y opacidad permanecen iguales. Cada par de líneas cambiado tiene sus números de línea antes/después, ruta, valores exactos y motivo en [clipped-text-style-diff.tsv](clipped-text-style-diff.tsv). Los diffs completos están en `target/clipped-text-audit/process-style.diff` y `workbench-style.diff`.

Las diferencias corresponden a: mínimo de 18 px de los iconos de paleta; ajuste de títulos/categorías de paleta; altura preferida de la paleta; ancho preferido de botones de cabecera; y altura/ajuste de rótulos de cabecera. El TSV explica individualmente cada línea con la regla correspondiente.

## Validación

- `ClippedTextAuditToolTest`: FXML sintético con botón estrecho, botón que cabe, menú estrecho, valor de salida de datos, cabecera con contenido diferente y contenido plegado. Comprueba detección y exclusión con motivo.
- `ClippedTextRegressionUITest`: dos estados de Process Designer, dos del Workbench y diálogo informativo, en español y tema claro; falla ante cualquier hallazgo.
- Los tests restauran el idioma y las preferencias de layout/visibilidad en `finally`; las selecciones y la visibilidad temporal se restauran igualmente. El tema se aplica a la escena sin cambiar preferencias persistidas.

| Test nuevo | Pruebas | Fallos / errores | Tiempo en la suite final |
|---|---:|---:|---:|
| `ClippedTextAuditToolTest` | 1 | 0 / 0 | 0,549 s |
| `ClippedTextRegressionUITest` | 1 (cinco estados) | 0 / 0 | 10,319 s |

La regresión representativa tardó 4,787 s en la pasada dirigida limpia; en la suite completa, con carga acumulada, tardó 10,319 s. La herramienta completa permanece opt-in y tarda aproximadamente 130 s por combinación.

**Suite completa**: una única ejecución final de `mvn -o -q -Plow-cpu test`, salida 0. **370 clases informadas, 2532 pruebas, 2531 ejecutadas, 1 omitida, 0 fallos y 0 errores**. Tiempo real de Maven: **543,5 s (9 min 3,5 s)**. La paridad/localización existente sigue verde (`I18nServiceTest`: seis pruebas sin fallos; además de las restantes pruebas i18n de la suite). Los informes anteriores se apartaron antes de esta ejecución, de modo que el total procede exclusivamente de sus XML nuevos.

La prueba omitida fue `Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain`, condicionada a la configuración de SoftHSM y la existencia del alias. No se modificó esa prueba.

Las verificaciones de grupos dieron 862 → 78 → 33 → 19 → 0 en español claro y ninguna pantalla aumentó su recuento. Las tres pasadas adicionales finales (en claro, en oscuro, es oscuro) terminaron con salida 0 y total 0; es claro conserva la pasada completa del último grupo. La comparación de estilo y la revisión de atributos FXML también pasaron. No se hizo prueba manual.

## Commits

```text
c48b79f test(ui): add opt-in skin text clipping audit
a5070b7 test(ui): verify clipping detection with synthetic FXML
3f078c9 test(ui): bound audit scenes and inspect owned captions and alternate states
854fd64 docs(ui): record clipping baseline 724/724/816/816
b45e10c test(ui): settle deferred initialization before auditing captions
4b9f854 docs(ui): record settled clipping baseline 782/782/879/879
8086590 test(ui): isolate and restore workspace preferences for every audit screen
7614ca4 docs(ui): record isolated clipping baseline 765/765/862/862
d059769 fix(ui): fit designer palette 784 to 0 and workbench startup headers 8 to 0
a8b56e1 fix(ui): fit key and certificate captions 45 to 0
0e5e65d fix(ui): fit conversion batch and history captions 14 to 0
83170b4 test(ui): exclude workbench data outputs and verify menu captions
8dc7a17 fix(ui): fit security and EMV captions 19 to 0
d886fdb test(ui): guard five Spanish light clipping states and preference restoration
```

Los commits `854fd64` y `4b9f854` conservan mediciones de calibración superadas. La línea base aceptada está en `7614ca4`, con la herramienta aislada de `8086590`. `83170b4` añade la exclusión de salidas de datos y prueba de menú sin cambiar sus recuentos. El commit final de documentación contiene este informe y los TSV; puede consultarse junto a todos los anteriores con `git log --oneline be9f7e8..HEAD`.

Revisión adicional del árbol FXML: mismas etiquetas, mismos hijos y mismos textos; las 572 diferencias de atributos son exclusivamente de tamaño o `wrapText`. El CSS conserva íntegro el contenido anterior y solo añade mínimos de ancho y ajuste de texto al final de «Legibility overrides». `pom.xml`, `.mvn/` y `crypto/` permanecen sin cambios.
