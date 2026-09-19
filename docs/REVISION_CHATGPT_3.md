# Tercera revisión de ChatGPT — `b6dc1d1`

Todo lo pedido está hecho. El refactor declarativo está mejor resuelto de lo que
lo planteé. Hay **un hallazgo**, y es el de siempre disfrazado de otra cosa.

## El refactor, comprobado

Fui a buscarle las costuras y aguanta:

- **Dos filas en el registro y ninguna inventada.** `A0`, `BU`, `A6`, `A8`,
  `CA`, `CY` no tienen esquema. La tentación con una tabla declarativa es
  rellenarla, porque añadir una fila cuesta nada y parece progreso.
- **La ambigüedad está prevenida por construcción, no resuelta en caliente.** El
  bloque estático rechaza al cargar dos esquemas que compartan dirección,
  código, error y longitud. Por eso `findFirst()` no puede elegir mal: no puede
  haber dos candidatos. Es la pregunta que traía preparada y ya estaba
  contestada.
- **`bodyLength()` se deriva de los campos**, así que los offsets no pueden
  desbordar el cuerpo. No hay forma de escribir un esquema que reviente al
  aplicarse.
- **Todo o nada.** Si un solo campo falla el tipo, no se devuelve ninguno. Nada
  de entregar los tres primeros campos y dejar el cuarto a medias.

Y la campaña del bloque C incluye casos de **una sola variable** —`MC-CVC3-UN-01`,
`MC-CVC3-ATC-02`, `VISA-LUK-COUNTER-01`— sin que nadie se lo pidiera. Es la
lección de Futurex aplicada por su cuenta: cinco capturas que difieren en un
campo aíslan ese campo, y una sola captura habría hecho pasar por correcta una
implementación sin separación de claves.

## El hallazgo: `VERIFIED` no lo verifica nadie

`EvidenceStatus` tiene dos valores. `PENDING_CAPTURE` se usa en las dos filas.
**`VERIFIED` no se usa en ningún sitio, y nada comprueba qué hace falta para
ponerlo.**

Tal como está, el día que llegue la captura `NC-00` alguien cambia una palabra
en una fila y el esquema pasa a declararse verificado. No hay nada que exija que
exista un vector, ni que ese vector se descomponga bajo ese esquema, ni que el
test siga ahí dentro de seis meses. El estado de evidencia es hoy un adjetivo.

Es exactamente el problema que llevamos toda la semana persiguiendo en otra
forma: la tabla de errores citaba un manual que no existía, el vector de `NC`
se citaba a sí mismo a través de mi revisión. Cada vez lo hemos arreglado a
mano. Aquí hay una oportunidad de arreglarlo **de una vez y por construcción**.

**La propuesta:** que el esquema lleve su propia muestra capturada — los bytes
exactos, y el identificador de la captura de la que salieron. Entonces:

- Un test recorre el registro y, por cada esquema `VERIFIED`, descompone su
  muestra y comprueba que sale lo que dice.
- Un esquema `VERIFIED` sin muestra hace fallar la suite.
- Una muestra que ya no descomponga hace fallar la suite.

Con eso, `VERIFIED` deja de ser una palabra que alguien escribe y pasa a ser
algo que el repositorio sabe comprobar. Y el flujo de trabajo queda honesto por
defecto: llega una captura, se pega la muestra, se sube el estado, y si alguna
de las dos cosas falta el build lo dice.

Son unas diez líneas y cierran la puerta por la que se nos ha colado esto tres
veces.

## Dos cosas menores, para que no sorprendan

**`firstExactMatch` es seguro por un invariante que vive en otra clase.** La
garantía de que no hay dos candidatos la impone el bloque estático de
`PayShieldBodySchemas`; si alguien relaja esa clave un día, `findFirst()` pasa a
elegir por orden de declaración y en silencio. Un comentario en el
descompositor que apunte al invariante evita ese desenlace.

**`PRINTABLE_ASCII` discrimina poco.** El campo de firmware acepta casi
cualquier cosa de nueve caracteres, así que lo que de verdad separa la respuesta
`NC` de otra son la longitud total y el que los primeros dieciséis sean
hexadecimales. Está bien así, pero conviene saberlo para no fiarse más de la
cuenta de la frase «el tipo tiene que coincidir». Y `HEX` acepta minúsculas,
cuando payShield emite mayúsculas: correcto para leer, algo laxo para validar.

## Qué toca ahora

1. **Las diez líneas de la muestra capturada.** Antes de que lleguen las
   capturas, porque después ya no se hace.
2. **La lista del bloque B**, secure messaging Visa y Mastercard, con el mismo
   formato que le han salido las dos anteriores. Es lo único de su encargo que
   no tiene todavía campaña de capturas.
3. Y entonces sí, esperar. Con `NC-00` y las del bloque C en la mano, cerrar
   `BU`, `A0`, `A6`, `A8`, `CA` y `CY` es añadir filas.

No he ejecutado su suite; su número (1988) cuadra con la base y sus tests, y no
voy a lanzar un Maven contra su worktree mientras trabaja.
