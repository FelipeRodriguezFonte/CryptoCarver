# Capturas de Cryptographic Calculator para secure messaging EMV

Esta campaña cierra el bloque B con vectores reproducibles de **secure
messaging de emisor**. Visa y Mastercard no se tratan como variantes de un
único perfil: la captura debe mostrar el perfil, la derivación, el relleno y
los bytes realmente autenticados antes de que se implemente cualquiera de los
dos.

No se inventa una APDU. La APDU de partida es la plantilla de cambio de PIN que
ofrezca la versión instalada de Cryptographic Calculator; se captura completa y
se reutiliza literalmente para los casos derivados.

## Entrega común

Antes de calcular, enviar `SM-INDEX-00`: ventana completa de **About/Version**
y menú EMV abierto donde se vea la ruta y el nombre exactos de la herramienta
de Secure Messaging / Issuer Script. Anotar versión, perfil de tarjeta y los
campos que estén deshabilitados.

Para cada ID devolver:

1. ventana completa, con desplegables, valores por defecto y resultado;
2. todos los inputs, incluso los que rellene la herramienta;
3. APDU clara completa, con `CLA INS P1 P2 Lc Data Le` cuando existan;
4. APDU/script protegido completo y export/copy-text si existe;
5. claves de sesión, datos de derivación, bloque de PIN claro y cifrado,
   entrada exacta al MAC, bloque tras relleno y MAC final;
6. texto de la herramienta que identifique algoritmo, modo, truncado y orden
   de cada operación.

No usar claves ni PAN de producción. Si la herramienta no muestra uno de esos
intermedios, conservar la captura y anotar expresamente la ausencia: no se
reconstruye ni se infiere fuera de ella.

## Resultado (25-09-2026) — resuelto con los ejemplos de la herramienta

Ruta real: EMV → Secure Messaging → MasterCard / Visa, tres pestañas (Session
key, PIN, MAC) con un ejemplo precargado coherente entre ellas. Se capturaron
con sus valores por defecto y `EmvSecureMessaging` reproduce todos los pasos
(`EmvSecureMessagingTest`).

- **Mastercard**: UDK por opción A desde MK y PAN/SqNr (16 dígitos), paridad
  impar; clave de sesión SKD sobre R = AC + nº de comando, tercer byte `F0`
  (izquierda) y `0F` (derecha), sin paridad; PIN en formato ISO 2 cifrado en
  TDES-ECB. El comando 2 descartó «AC XOR n».
- **Visa**: clave de sesión = UDK con el ATC en XOR en los dos últimos bytes de
  la mitad izquierda y su complemento en los de la derecha; PIN en formato 0
  combinado en XOR con `00000000` y los 4 últimos bytes de UDK-A, enviado como
  `08` || bloque || `80…`, TDES-ECB.
- **MAC** (los dos): ISO 9797-1 alg. 3, relleno 2, sobre `CLA INS P1 P2 Lc`,
  ATC, AC y datos del comando.

La cobertura de la cabecera en el MAC queda fijada por los propios ejemplos:
entra entera.

## Constantes de la campaña

| Identificador | Valor |
|---|---|
| `K.SMI` | `0123456789ABCDEFFEDCBA9876543210` |
| `K.SMC` | `0123456789ABCDEFFEDCBA9876543210` |
| `PAN` | `4111111111111111` |
| `PSN` | `00` |
| `ATC.1` | `0001` |
| `PIN` | `1234` |

Si la UI sólo admite una clave de script, usar `K.SMI` y dejar visible que es
una clave compartida. Si separa las claves por mitades, introducir la primera
mitad de `K.SMI`/`K.SMC` en A y la segunda en B; no duplicar una mitad.

---

## 1. Visa CSK

Ruta esperada: **EMV → Secure Messaging / Issuer Script → Visa (CSK)**. Usar el
literal mostrado por `SM-INDEX-00` si difiere.

### `VISA-CSK-00` — vector base

| Campo de la herramienta | Valor |
|---|---|
| Profile / scheme | Visa CSK, seleccionado explícitamente |
| MK-SMI / encryption key | `K.SMI` |
| MK-SMC / MAC key | `K.SMC` |
| PAN | `PAN` |
| PAN sequence number | `PSN` |
| ATC | `ATC.1` |
| Script | plantilla de PIN change de la herramienta |
| New PIN | `PIN` sólo en el campo de helper de la UI |
| APDU/header options | valor por defecto visible |
| Padding / derivation / MAC options | valor por defecto visible |

Copiar por separado la APDU clara, las claves de sesión SMI y SMC, los datos de
derivación, el bloque de PIN claro, el PIN cifrado, los bytes que entran en el
MAC, el bloque rellenado, el MAC y la APDU final. Si la herramienta decide qué
parte se autentica, capturar ese selector o texto literal.

### `VISA-CSK-HDR-01` — aislar la cabecera APDU

Duplicar `VISA-CSK-00` y cambiar sólo un byte de cabecera que la UI permita
editar (preferiblemente `P1`, de `00` a `01`). No cambiar `Lc`, `Data`, PIN,
PAN, PSN, ATC, claves ni opciones. Si la plantilla no permite editar ningún
byte de cabecera, enviar la ventana y marcar este caso como no disponible; no
fabricar una APDU manualmente.

Devolver los mismos intermedios. El par demuestra exactamente si y cómo la
cabecera participa en el MAC del perfil Visa.

## 2. Mastercard SKD

Ruta esperada: **EMV → Secure Messaging / Issuer Script → Mastercard (SKD)**.
No seleccionar una opción genérica o Visa si el nombre cambia; usar el literal
confirmado por `SM-INDEX-00`.

### `MC-SKD-00` — vector base

| Campo de la herramienta | Valor |
|---|---|
| Profile / scheme | Mastercard SKD, seleccionado explícitamente |
| MK-SMI / encryption key | `K.SMI` |
| MK-SMC / MAC key | `K.SMC` |
| PAN | `PAN` |
| PAN sequence number | `PSN` |
| ATC | `ATC.1` |
| Script | plantilla de PIN change de la herramienta |
| New PIN | `PIN` sólo en el campo de helper de la UI |
| APDU/header options | valor por defecto visible |
| Padding / derivation / MAC options | valor por defecto visible |

Copiar la misma lista de salidas de `VISA-CSK-00`, incluyendo cualquier clave
de sesión o bloque que el perfil presente con otro nombre. La captura debe
dejar visible la diferencia de derivación, relleno y bytes autenticados; no
basta con conservar el MAC final.

### `MC-SKD-HDR-01` — aislar la cabecera APDU

Duplicar `MC-SKD-00` y cambiar únicamente el mismo byte lógico de cabecera que
en `VISA-CSK-HDR-01`. Si el perfil Mastercard ofrece otros valores válidos,
mantener todos los demás inputs idénticos. Si el control no existe, documentar
la limitación con la ventana completa, sin editar la APDU fuera de la UI.

Copiar los mismos intermedios que en el vector base. Comparado con
`MC-SKD-00`, este par identifica la cobertura del MAC de Mastercard sin
atribuir una diferencia a datos, clave o relleno.

## Criterio de aceptación

Una ficha sólo se convierte en vector de test cuando permite registrar, sin
suposiciones:

- producto, versión, pestaña y perfil exactos;
- algoritmo, longitud y rol de las claves de entrada y de sesión;
- APDU clara y APDU protegida completas;
- PIN cifrado, bytes de entrada al MAC, relleno y MAC final;
- un segundo caso que cambie sólo una cabecera APDU, cuando la UI lo permita.

Hasta entonces `generateScriptMAC` continúa siendo genérico: no se deriva un
perfil Visa o Mastercard del nombre comercial ni de un resultado aislado.
