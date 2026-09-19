# Qué sacar de BP-Tools — lista de capturas

Felipe tiene BP-Tools (HSM Commander y Cryptographic Calculator) en la sesión
Windows de Parallels. Yo no puedo conducirla: los eventos sintéticos de macOS no
llegan al invitado. Así que el trato es: aquí digo exactamente qué meter en cada
campo y qué devolver; él manda pantallazo.

Cada captura de esta lista desbloquea algo que hoy está parado por falta de
documentación pública. Están en orden de valor.

## Cómo mandarlos

- **La ventana entera**, no sólo el resultado. Lo que cerró el Thales Key Block
  no fue el bloque: fueron el KBEK y el KBAK que la herramienta imprime al lado.
  Los valores intermedios son la mitad del vector.
- **Sin tocar los desplegables que no se mencionen.** Si un campo trae un valor
  por defecto y lo cambias, el resultado deja de ser comparable con lo que yo
  espero y no sabré si la diferencia es mía o tuya.
- Si un campo que pido no existe con ese nombre, manda la pestaña tal cual está
  y ya te digo yo. Los nombres de abajo son mi mejor conjetura del menú, no
  evangelio.

## Constantes de la casa

Usa estos valores siempre que la captura no diga otra cosa. Así todo el material
es comparable entre formatos y entre agentes.

| Nombre | Valor |
|---|---|
| Clave de 16 bytes (doble) | `0123456789ABCDEFFEDCBA9876543210` |
| Clave de 24 bytes (triple) | `0123456789ABCDEF8080808080808080FEDCBA9876543210` |
| Clave AES-128 | `000102030405060708090A0B0C0D0E0F` |
| PAN | `4111111111111111` |
| PAN sequence | `00` |
| PIN | `1234` |

---

## 1. Atalla AKB — el bloqueo más caro

Tres intentos y cuatro fuentes sin cerrar el formato. La herramienta lo calcula.

**Dónde:** Cryptographic Calculator → Key Management (o Key Blocks) → Atalla /
AKB.

**Qué meter:**

- MFK (Master File Key): `0123456789ABCDEF8080808080808080FEDCBA9876543210`
- Clave a proteger: `0123456789ABCDEFFEDCBA9876543210`
- Header / atributos: **lo que traiga por defecto.** No lo cambies.

**Qué necesito ver:**

1. El AKB completo, con sus comas si las lleva.
2. La cabecera desglosada si la herramienta la explica campo a campo.
3. **Cualquier valor derivado que imprima** — variantes de la MFK, claves de
   cifrado y de MAC. Esto es lo que de verdad hace falta.
4. El MAC por separado si lo muestra aparte.

**Segunda pasada, sólo si la primera sale bien:** repite con la misma MFK y la
misma clave pero cambiando **un solo atributo del header** (el uso de la clave,
por ejemplo). Con dos bloques que difieren en un bit de cabecera deduzco si la
cabecera entra en el MAC y si entra en la derivación. Es la pregunta que ninguna
fuente contesta.

## 2. Thales Key Block AES (version ID `1`)

El 3DES ya está cerrado y verificado contra la herramienta. El AES lo rechacé
por nombre porque **deriva** sus claves en vez de variarlas, y no tengo vector.

**Dónde:** el mismo sitio donde sacamos el bloque `S00072B0TN00E0002...`, pero
eligiendo el esquema AES / version ID `1`.

**Qué meter:**

- LMK: la LMK de test de Key Block AES que publica el manual en la cláusula
  8.8.2. Si la herramienta la trae precargada como «test LMK», úsala y dime cuál
  es.
- Clave a proteger: `000102030405060708090A0B0C0D0E0F`
- Key usage / mode of use: los mismos que el ejemplo de 3DES (`B0`, `N`) si te
  deja; si no, lo que salga.

**Qué necesito ver:** el bloque, **la KBEK y la KBAK derivadas**, y el relleno si
lo enseña. Igual que la vez que salió bien.

## 3. Thales variante LMK — confirmación contra hardware

Esto ya lo tengo implementado, pero descansa en un manual cuya cláusula 7.1
imprime `6A` donde 7.2.3 y su propio Ejemplo 1 dicen `A6`. Dos fuentes muy
citadas de internet lo ponían mal, cada una en un byte distinto. Quiero una
confirmación que no sea papel.

**Dónde:** Cryptographic Calculator → Thales → Key under LMK (esquema de
variante, no key block).

**Qué meter:**

- LMK: la LMK de test de Thales que traiga por defecto. **Dime cuál es**, es
  medio vector.
- Clave: `0123456789ABCDEFFEDCBA9876543210`
- Key type: `002` (ZMK)
- Scheme: `U`

**Qué necesito ver:** el criptograma y el KCV. Con eso confirmo de un vistazo si
la variante del tipo va al byte 0 y la del esquema al byte 8.

**Y una segunda, barata:** lo mismo con key type `402` (CVK) y scheme `U`. Dos
tipos distintos bajo la misma LMK aíslan la variante del tipo de todo lo demás.

## 4. Futurex MFK y SafeNet KM

Mismo patrón que Atalla, misma razón. Si el Cryptographic Calculator los tiene:

- Clave maestra: `0123456789ABCDEF8080808080808080FEDCBA9876543210`
- Clave a proteger: `0123456789ABCDEFFEDCBA9876543210`
- Todo lo demás por defecto.

Y otra vez: **los valores derivados que imprima**, no sólo el bloque.

Si alguno no está en la herramienta, dímelo y lo cierro como fuera de alcance en
vez de dejarlo colgando en la tabla de paridad.

---

# Para los otros dos

Estas no me desbloquean a mí, pero sin ellas Luna y ChatGPT van a adivinar, que
es exactamente lo que hemos acordado no hacer. Son más largas; sácalas cuando
ellos lleguen ahí y te las pidan, o de golpe si tienes la tarde.

## 5. HSM Commander — tramas de comando (ChatGPT, hueco 7)

La receta campo a campo y el orden de reutilización están en
[`CAPTURAS_HSM_COMMANDER_PAYSHIELD.md`](CAPTURAS_HSM_COMMANDER_PAYSHIELD.md).
Esta sección queda como índice de prioridad.

Aquí el valor no está en el resultado criptográfico sino en **la trama de texto
literal**, petición y respuesta, tal como viajan. Es lo que va a descomponer el
banco de comandos.

Por cada comando: manda la petición y la respuesta **en crudo**, tal como las
muestre la consola.

Prioridad:

1. `A0` — generar clave (los tres modos: 0, 1, 2)
2. `A6` — importar clave bajo ZMK
3. `A8` — exportar clave bajo ZMK
4. `BU` — KCV de una clave
5. `CA` — traducir bloque de PIN de ZPK a ZPK
6. `CY` — verificar CVV
7. `DC` — verificar PIN (IBM 3624)
8. `NC` — diagnóstico (es la más corta, buena para empezar y ver el formato)

Y **una respuesta de error a propósito**: manda un `A6` con una clave mal
formada. El código de error traducido es la mitad de lo que hace útil un
descompositor y no viene en ningún sitio con ejemplos reales.

## 6. Esquemas: CVC3, DS, LUK (ChatGPT, hueco 8)

Casi nada de esto está bien documentado en abierto. La herramienta lo calcula.

**Mastercard CVC3** — Cryptographic Calculator → EMV → CVC3 (o Contactless):

- IMK: `0123456789ABCDEFFEDCBA9876543210`
- PAN `4111111111111111`, PAN seq `00`
- ATC: `0001`
- Unpredictable Number: `00000000`
- Track 2 / IVCVC3: lo que traiga por defecto

Necesito: CVC3 de track 1 y de track 2, **y las claves intermedias** — ICC MK,
IVCVC3.

**Mastercard Data Storage (DS)** y **Visa LUK / MSD**: mismos datos de tarjeta,
todo lo demás por defecto, y otra vez los intermedios.

## 7. Secure messaging EMV (ChatGPT, hueco 3)

La diferencia entre Visa y Mastercard está en el relleno, la derivación de
sesión y qué entra en el MAC. Un vector de cada uno, con los mismos datos, hace
visible la diferencia.

Cryptographic Calculator → EMV → Secure Messaging / Script:

- IMK: `0123456789ABCDEFFEDCBA9876543210`
- Tarjeta: PAN y seq de la casa, ATC `0001`
- Script: un PIN change con PIN `1234`
- Una captura con perfil **Visa (CSK)** y otra con perfil **Mastercard (SKD)**

Necesito: el comando cifrado completo, la clave de sesión, el MAC, y el bloque
antes de cifrar si lo enseña.

## 8. MAC y bloques de PIN (Luna)

Rápidas, y cierran cosas que hoy tiene sólo contra sí misma.

**ISO 9797-1 MAC**, Cryptographic Calculator → MAC:

- Clave: `0123456789ABCDEFFEDCBA9876543210`
- Datos: `48656C6C6F2C20776F726C6421` (`Hello, world!` en ASCII)
- Una captura por **algoritmo 1, 2, 4 y 6**, y por cada uno **método de relleno
  1 y 2**. Son ocho, pero es la misma pantalla cambiando dos desplegables.

**Bloques de PIN**, Cryptographic Calculator → PIN Blocks:

- PIN `1234`, PAN `4111111111111111`
- Formatos **0, 1, 2, 3 y 4** (ISO-0 a ISO-4). El 4 es AES y es el que más falta
  hace.
- Para el 4, clave `000102030405060708090A0B0C0D0E0F`.

---

## Qué hago yo con esto

Cada captura entra en el repositorio como test con el origen citado — «BP-Tools
Cryptographic Calculator, captura de 2026-09-19» — igual que el vector del Key
Block. Un vector de la herramienta contra la que buscamos paridad vale más que
tres descripciones de internet, y ya hemos visto que las descripciones se
contradicen.

Lo que **no** hago: implementar nada de lo de arriba sin su captura. Un round
trip verde no prueba nada; `wrap` y `unwrap` se ponen de acuerdo perfectamente
estando los dos mal. Ya nos pasó dos veces en esta rama.
