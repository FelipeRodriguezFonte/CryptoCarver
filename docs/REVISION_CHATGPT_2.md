# Segunda revisión de ChatGPT — rama `codex/hsm-schemas`, commit `0fcf88d`

Los seis puntos de la revisión anterior están cerrados. Uno de ellos mejor de lo
que pedí. Queda **una cosa nueva**, en dos sitios, y es sutil.

## Lo que está bien

**La tabla de errores.** Pedí que separase lo verificado de lo recordado.
Borró once entradas y dejó una. Tirar código que parece correcto y que
probablemente lo era, porque no se puede sostener la cita, es la decisión
difícil de esta clase de trabajo, y la tomó en la dirección correcta.

**El formato.** De 68 a 242 líneas en el códec. `parsePayload`, que era la línea
ilegible donde estaba el trabajo sutil, ahora se lee.

**El descompositor de `NC`.** No sólo descompone: **se niega a descomponer una
forma que no ha visto**. `PayShieldNcResponse.from` devuelve `Optional.empty()`
en cuanto la longitud no es la exacta capturada, en vez de partir por offsets
fijos y devolver basura con aire de dato. Eso es justo el reflejo que hay que
tener, y no se lo había pedido.

**El documento de capturas.** Es mejor que el mío. La idea de identificar cada
valor producido (`A0-0.keyUnderLmk`) y reutilizarlo por identificador en las
capturas siguientes resuelve un problema que yo no había visto: no se puede
pedir «una clave bajo LMK» sin saber qué juego de LMK tiene la consola, así que
hay que encadenar las salidas. Y dice explícitamente que la respuesta `NC` que
ya está en el repositorio **no se pegue como entrada**, porque el objetivo es
una captura independiente. Eso es entender para qué sirve un vector.

**El traslado a `payments.fxml`** está limpio: en `keys.fxml` no queda nada del
banco, y las cuatro menciones a payShield que quedan en `KeysController` son
comentarios míos sobre los capítulos 7 y 8 del manual.

## Lo que falta: la cita da la vuelta

El test del vector `NC` dice:

> `Source: raw NC response reproduced in docs/REVISION_CHATGPT_1_Y_PAQUETE_2.md`

Ese documento es **mi revisión**, y el único sitio del que yo saqué ese valor
fue su propio test. La cita apunta a un documento cuyo conocimiento del dato
viene del código que está citando. Es un bucle: el valor no ha ganado ni un
gramo de procedencia, pero ahora lo parece.

El texto de al lado es honesto —dice que la procedencia original no se
registró— y la intención está bien. El problema es el encabezado: quien lee por
encima ve `Source:` seguido de una ruta y para de leer. Una cita que no resuelve
es peor que ninguna, que es exactamente lo que dijimos de la URL del manual.

**Arreglo:** que no empiece por `Source:`. Algo como «origen no registrado: este
valor ya estaba en el árbol cuando se planteó la pregunta de procedencia, y
ninguna captura lo respalda. La sustituye `NC-00` de
`CAPTURAS_CONSOLA_HSM_PAYSHIELD.md`».

**Y el mismo bucle, más pequeño, en la tabla de errores.** `00` se traduce hoy
como *"No error (verified by captured NC response)"*, y esa captura es
justamente la que no tiene procedencia. El único código verificado descansa
sobre el único dato sin verificar. Que diga «pendiente de la captura `NC-00`»
hasta que llegue.

No es grave y se arregla en dos líneas. Lo señalo porque es la misma clase de
error que el resto de la entrega ya corrigió bien, y porque en seis meses nadie
va a seguir la ruta.

## Lo demás, menor

`CAPTURED_DATA_LENGTH = 25` hace que la descomposición dependa de la longitud
total exacta. Es la decisión correcta y está documentada; sólo conviene tener
presente que un firmware cuyo sufijo mida distinto quedará opaco en vez de mal
descompuesto — que es el fallo que se prefiere.

No he vuelto a ejecutar su suite. El número que da (1958) cuadra: la base
`e5c7358` daba 1943 aquí, y los quince tests suyos lo completan. Y hace un rato
me he estropeado yo mismo dos ejecuciones por lanzar dos Maven contra el mismo
`target/`, así que no voy a lanzar uno contra su worktree.

---

# Qué hace ahora

## 1. Las dos líneas de la cita

Antes que nada, porque es lo que se olvida.

## 2. No esperar a las capturas de brazos cruzados

El informe dice que el siguiente paso real es obtener las capturas. Es verdad
que sin ellas no se pueden implementar los descompositores — pero sí se puede
hacer que **implementarlos deje de ser trabajo de programación**.

Ahora mismo `NC` tiene su propio record y su propio parser. Multiplicado por
dieciséis comandos eso son dieciséis clases, dieciséis tests y dieciséis
revisiones. La alternativa: **una descripción declarativa del cuerpo por
comando** —una lista de campos con nombre, longitud y tipo— y un único
descompositor que la aplique, con la misma negativa a actuar cuando la forma no
encaje con lo declarado.

Con eso, cada captura que llegue se convierte en **una fila de tabla y un
test**, no en una clase nueva. Y algo más importante: el día que un campo esté
mal, se ve en la tabla, no repartido por dieciséis parsers.

Es el trabajo que se puede hacer hoy, sin ningún vector, y es el que decide si
el bloque A se cierra en una tarde o en dos semanas cuando lleguen las capturas.

## 3. El bloque C, que no está bloqueado

Para HCE y tokenización —CVC3, DS, LUK— la herramienta no es la consola HSM sino
la **calculadora** de la herramienta externa, que los calcula todos e imprime las claves
intermedias. Ese oráculo está disponible ahora mismo y no depende de las
capturas del bloque A.

Hoy ese mismo método cerró cinco formatos de protección de clave en una tarde,
incluido Atalla AKB, que llevaba tres negativas por falta de fuente. Que escriba
su lista de capturas para el bloque C con el mismo formato que la del A —que le
ha salido muy bien— y la pasamos en el mismo viaje.
