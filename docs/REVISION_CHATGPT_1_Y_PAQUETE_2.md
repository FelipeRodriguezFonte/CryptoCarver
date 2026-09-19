# Revisión de la primera entrega de ChatGPT, y qué sigue

Rama `codex/hsm-schemas`. Revisado el bloque A: códec payShield, catálogo de 16
comandos, tabla de errores, nodos de Process Designer y panel JavaFX.

## Veredicto

La entrega está bien encaminada y lo más importante de todo es lo que **no**
hizo: no se inventó los cuerpos de los quince comandos que no puede verificar.
Los dejó opacos y lo dijo en el informe. Esa es exactamente la disciplina que
pedía el encargo, y es lo difícil de sostener cuando la alternativa es entregar
algo que parece más completo.

Dos cosas más que están bien y merecen decirse:

- **La captura de `NC` es real y parsea.** `0000ND007B44AC1DDEE2A94B0007-E000`
  se descompone en cabecera, `ND`, error `00` y datos. Es lo mejor de la
  entrega.
- **El comentario sobre `EM` demuestra lectura, no conjetura.** Haber visto que
  `EM` delimita un tráiler opcional y no termina todos los mensajes es sutil, y
  equivocarse ahí habría producido un códec que falla sólo con los mensajes sin
  tráiler, que son la mayoría.

Ahora lo que hay que corregir antes de seguir.

---

## 1. El estilo del código. No es una manía

El código está minificado: varias sentencias por línea, declaraciones agrupadas
(`private final String displayName,responseCode;`), el enum entero de 16
comandos en una sola línea de 600 caracteres. `PayShieldCommand.java` son nueve
líneas para lo que en este repositorio son sesenta.

Importa porque el resto del repositorio hace lo contrario, y a propósito. Una
sentencia por línea y Javadoc que explique **dónde está la trampa**, no qué hace
el método. Y donde más importa es justo donde está peor: `parsePayload` es el
método que hace el trabajo sutil de este bloque —encontrar el delimitador,
separar tráiler de cuerpo, decidir dónde acaba el mensaje— y hoy es una línea
que nadie va a releer dentro de seis meses.

Referencia de estilo, por si ayuda: `ThalesKeyBlockOperations` y
`AtallaAkbOperations` en `main`. Miren especialmente los comentarios que
explican por qué algo es como es y no de la forma evidente. Eso es lo que hay
que imitar.

## 2. La procedencia del vector de `NC` no está escrita

Es lo mejor que hay en la entrega y no dice de dónde salió. Un vector externo
sin línea de procedencia es indistinguible de uno inventado en cuanto pasan unos
meses, y entonces deja de servir como evidencia.

Formato que usamos en `main`:

```java
/**
 * Source: EFTLab BP-Tools Cryptographic Calculator 21.06, Thales Key Block,
 * captured 2026-09-19.
 */
```

Uno de esos por cada vector externo. Si el de `NC` salió de una captura, de un
manual o de un foro, que lo diga, incluido el foro.

## 3. La tabla de errores cita un documento que el propio informe dice no tener

`PayShieldErrorCatalog` atribuye sus doce entradas a *payShield 10K Core Host
Commands v1, PUGD0537-004*. El mismo informe dice, dos párrafos después, que
falta el Core Host Commands Manual.

Las dos cosas no pueden ser ciertas. O tiene el documento —y entonces que diga
dónde está y en qué sección— o las doce entradas salieron de la memoria y la
cita hay que quitarla. `translate("14")` devuelve hoy «PIN encrypted under LMK
pair 02-03 is invalid», que es una frase lo bastante específica como para que
nadie la ponga en duda, y ese es justo el problema.

Lo correcto mientras no haya fuente: las entradas verificadas con su cita, las
demás marcadas como no verificadas de forma que se vea en la interfaz. Un
«desconocido» honesto vale más que una traducción plausible.

## 4. Los tests de ida y vuelta miden contra sí mismos

`commandRoundTripPreservesBodyAndTrailer` compone con el códec y parsea con el
mismo códec. Eso pasa igual de verde si las dos mitades están mal, y es
literalmente el punto que traía el encargo: en esta rama ese patrón escondió un
bug real de TR-31 durante meses, porque el escritor y el lector estaban de
acuerdo en la convención equivocada.

No hay que borrarlos: como tests de framing valen. Hay que **renombrarlos** para
que digan lo que prueban (`framingSurvivesARoundTrip`) y dejar el nombre serio
para los que van contra material externo. Y un detalle: el cuerpo
`"DIAGNOSTIC"` está inventado, un `NC` real no lleva cuerpo. Como prueba de
framing da igual; llamándose como se llama, engaña.

## 5. El banco está en el módulo equivocado

El panel está en `keys.fxml`, que tiene 1128 líneas y donde ya viven los paneles
de Thales LMK y Thales Key Block. Existe `payments.fxml`, que es donde encaja un
banco de comandos host.

Es mejor diseño y además resuelve la peor colisión: ahora mismo ChatGPT y yo
editamos los mismos cinco ficheros compartidos (`keys.fxml`, `KeysController`,
`ModuleTextCatalog`, `messages_*.properties`, `UiNavigationRegistry`). Mover el
panel a `payments.fxml` deja sólo las tres últimas, que son de una línea por
entrada y se fusionan sin dolor.

## 6. Cosas de intendencia

- **Comprometer los cambios.** Siguen sin estar en Git.
- **Rebasar.** Su base es anterior a `e5c7358`, que añade `AtallaAkbOperations`
  y el esquema AES del Key Block. No tocan sus ficheros, pero mejor pronto.
- **Un fallo mío, no suyo.** La URL del manual que aparece en el `@see` del
  códec se la di yo en el encargo, y es un servidor de demostración de terceros,
  no de Thales. Puede desaparecer mañana. Que cite el documento por título,
  número y cláusula, y que quite la URL. Corrijo el encargo original también.

---

# Lo que le decimos que haga ahora

## Primero: el bloqueo ya no existe

El informe da por bloqueados los quince comandos y los bloques B y C por falta
de documentación. **No lo están.** Felipe tiene **BP-Tools HSM Commander**
corriendo, que compone tramas reales y enseña respuestas reales, incluidos los
códigos de error. Para nuestros efectos, eso *es* el Core Host Commands Manual,
y además es mejor: no describe el formato, lo produce.

Hoy ese método cerró cinco formatos de protección de clave que llevaban semanas
parados —Atalla AKB entre ellos, que se había rechazado tres veces por falta de
fuente—. Funciona.

Así que la regla cambia de «si no puedo verificarlo, no lo implemento» a **«si
no puedo verificarlo, escribo qué hay que capturar»**. Un mensaje de dos
minutos en lugar de un bloqueo.

## Tarea 1 — Arreglar los seis puntos de arriba

Antes de código nuevo. El de estilo es el que más trabajo da y el que más rinde.

## Tarea 2 — La lista de capturas del bloque A

Un documento, no código, con **la lista exacta** de lo que necesita de HSM
Commander. Por cada comando: qué poner en cada campo de la consola, y qué
copiar de vuelta. Orden sugerido, de menos a más cuerpo:

1. `NC` — ya tiene la respuesta; que pida la **petición** para cerrar el par.
2. `BU` — KCV de una clave conocida. Cuerpo mínimo y comprobable contra nuestro
   propio cálculo de KCV, que ya existe en el repositorio.
3. `A0` — generar clave, en los modos 0, 1 y 2. Tres tramas que difieren en un
   campo aíslan ese campo.
4. `A6` y `A8` — importar y exportar bajo ZMK.
5. `CA` y `CY` — traducir bloque de PIN, verificar CVV.

Y la que más falta hace y menos cuesta: **una respuesta de error de verdad**.
Que pida un `A6` con una clave mal formada. Eso convierte la tabla de errores de
recuerdo en evidencia, y un descompositor que traduce mal un código de error es
peor que uno que no lo traduce.

El formato de la lista: como `docs/CAPTURAS_BPTOOLS.md` en `main`. Valores
concretos en cada campo, y la instrucción de mandar la ventana entera —lo que
cerró el Key Block no fue el bloque, fueron las claves derivadas que la
herramienta imprime al lado—.

## Tarea 3 — Descomponer, no sólo enmarcar

Con las capturas en la mano, el salto de valor no es enmarcar tramas: es coger
una trama que alguien capturó y decir qué campo es cada cosa. Hoy `NC` parsea a
`data = "7B44AC1DDEE2A94B0007-E000"`, un churro. Debería decir: valor de
comprobación de la LMK `7B44AC1DDEE2A94B`, versión de firmware `0007-E000`.

Eso, por comando, con su vector detrás, es el bloque A entero.

## Los bloques B y C, después

Mismo método. Para el C —CVC3, DS, LUK— la herramienta es el **Cryptographic
Calculator**, no el HSM Commander, y calcula todos ellos imprimiendo las claves
intermedias. Casi nada de eso está bien documentado en abierto, así que es el
bloque donde el oráculo vale más. Pero primero A, cerrado y con evidencia.
