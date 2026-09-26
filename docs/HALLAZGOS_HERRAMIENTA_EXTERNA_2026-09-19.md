# Lo que salió de las capturas de la herramienta externa del 19-09-2026

Catorce capturas de la calculadora de la herramienta externa, en cuatro tandas.
Todas las preguntas abiertas quedaron cerradas salvo el desglose de la
cabecera Atalla, del que sólo se conocen tres hechos sueltos. Este documento
es el registro de qué se dedujo, de qué sigue sin saberse y de qué haría falta
capturar para cerrarlo.

Método, para que conste: cada construcción se dedujo del par entrada/salida y se
comprobó reproduciendo el resultado carácter por carácter. Lo que no se pudo
reproducir no se implementó.

---

## 1. Atalla AKB — resuelto

El formato que tres intentos y cuatro fuentes no habían conseguido cerrar.

```
1PUNE000,B17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008,4FFC0BC8BCC1980E
```

- **Clave de cifrado = MFK ⊕ 45…45** (24 bytes).
- **Clave de MAC = MFK ⊕ 4D…4D**.
- **Cifrado: 3DES-CBC con los ocho bytes ASCII de la cabecera como IV.** Ahí
  está todo el mecanismo de ligadura: cambia un carácter de la cabecera y la
  clave sale corrupta, no sólo falla el MAC. Y explica por qué la cabecera mide
  exactamente ocho caracteres.
- **MAC: 3DES CBC-MAC con IV cero sobre la cabecera y el campo de clave
  cifrado**, ocho bytes completos, sin truncar.

Es decir: el mismo método de variantes E/M que ANSI X9.143 usa en sus versiones
A y C, en un formato que es anterior. No lo esperaba.

Implementado en `AtallaAkbOperations`, 18 tests.

**Dos avisos que van en el código.** Primero, `44` reproduce el vector igual que
`45`, y `4C` igual que `4D`: DES ignora el bit bajo de cada byte de clave, así
que una constante de variante no se puede fijar más allá de su bit de paridad
con un solo vector. Se escribe `45`/`4D` porque son la `E` y la `M` de X9.143.
Segundo, el MAC va sobre el **texto cifrado**, igual que en el Thales Key Block.
Dos formatos independientes, la misma trampa.

### La regla de relleno, con el segundo vector

El campo de clave mide **siempre 24 bytes**, y el hueco se rellena con un byte
repetido que **nombra la longitud de la clave**:

| Clave | Relleno |
|---|---|
| 8 bytes | `53` × 16 |
| 16 bytes | `44` × 8 |
| 24 bytes | ninguno |

Leídos como ASCII, `53` es `'S'` y `44` es `'D'`: *single* y *double*. Esa
lectura de las letras es una inferencia de dos puntos y no una fuente, pero la
regla que describe está observada. El caso de 24 bytes sale por aritmética.

### La cabecera: poco, pero verificado

Tres bloques con la misma clave y la misma MFK, cambiando un carácter de la
cabecera cada vez, dan dos cosas.

La primera es **la prueba directa de que la cabecera es el vector de
inicialización**: no cambia nada más de la entrada, y cambian el campo de clave
entero y el MAC.

| Cabecera | Campo de clave |
|---|---|
| `1PUNE000` | `B17A04DC…7948C008` |
| `1PUNE100` | `1254CC79…02443390` |
| `1SUNE100` | `4969F3DD…20A65140` |

La segunda es lo que la herramienta dijo sin que se lo preguntara. Marcó
`1PUNE000` como `[Valid]` y las otras dos como `[INVALID, check B5]`. Como en la
segunda el único carácter cambiado es el de la posición 5 contando desde cero,
eso fija **la numeración: la herramienta llama B0 a B7 a los bytes de la
cabecera**. Y en la tercera, que cambia además la posición 1 de `P` a `S`, sigue
quejándose sólo de B5 — luego `S` es aceptable ahí.

Total de lo que se puede afirmar: los bytes se numeran B0–B7, el byte 5 rechaza
el `1`, y el byte 1 acepta `P` y `S`. **Eso no es una tabla de campos** y este
banco no finge tenerla. El informe imprime la cabecera posición por posición con
esos nombres para que una queja del HSM sobre «B5» se pueda localizar, y nada
más.

Y un detalle de comportamiento que sí cambia el código: **la herramienta
construyó el bloque igualmente**. La validez de la cabecera es orientativa, no
se impone. Un banco que rechazara esas dos cabeceras rechazaría bloques que un
dispositivo real produce, así que leer un AKB no depende de ella.

## 2. Thales Key Block AES (versión `1`) — resuelto

Rechazado por nombre hasta hoy porque **deriva** sus claves en vez de variarlas,
y el capítulo 8 del manual dice «una variante de la LMK» para las dos versiones.

- **Derivación: la de ANSI X9.143 versión D.** AES-CMAC sobre ocho bytes de
  datos de derivación, `contador | uso (2) | separador 00 | algoritmo (2) |
  longitud en bits (2)`, repetido con el contador hasta llenar la longitud de la
  KBPK. Uso `0000` para cifrar, `0001` para MAC.
- **Cifrado: AES-CBC con los dieciséis caracteres de la cabecera como IV** —
  toda la cabecera, un bloque AES completo, donde el esquema 3DES toma los ocho
  primeros.
- **MAC: AES-CMAC sobre cabecera y datos cifrados, truncado a ocho bytes.**

**La trampa está en el ancho.** Son ocho bytes entrando en un cifrador de bloque
de dieciséis, así que se aplica el relleno propio de CMAC (`80 00…`) y entra la
segunda subclave. Rellenarlos con ceros hasta el bloque completo —lo evidente, y
lo primero que hice— produce una clave con toda la pinta de ser correcta que no
lo es, y nada se queja hasta que un HSM rechaza el bloque.

Implementado en `ThalesKeyBlockOperations`, 12 tests nuevos. La versión `0`
sigue igual.

## 3. Thales esquema de variante — confirmado, sin cambios

Esto ya estaba implementado, pero descansaba en un manual que se contradice a sí
mismo entre las cláusulas 7.1 y 7.2.3. Ahora tiene dos vectores de hardware:

| Variante | Criptograma bajo el par 00-01, esquema U |
|---|---|
| 1 | `A12049CC30D20BE0DC6E5E2A98FADE9E` |
| 2 | `BD7F64B9F9875B731BAED72101DEA65B` |

El código los reproduce sin tocar nada. Y el segundo bloque es el que importa:
el esquema U aplica `A6` a la parte izquierda y **`5A` a la derecha**. Una
implementación que use `A6` en las dos acierta el primer bloque y falla el
segundo, que es exactamente el error que un chequeo a mano no ve.

Fijado en `ThalesLmkExternalVectorTest`.

## 4. Futurex — resuelto

Con cinco criptogramas de la misma clave bajo la misma MFK, uno por
modificador, la regla sale limpia: el cifrado es **3DES-ECB liso bajo la MFK**,
y el modificador se aplica XOR-ando **modificador × 8 al primer byte de cada
parte de 8 bytes**.

| Modificador | Constante |
|---|---|
| 0 | `00` |
| 1 | `08` |
| 2 | `10` |
| 3 | `18` |
| 4 | `20` |

Es el modificador desplazado tres bits a la izquierda. Bonito de descubrir y
peligroso de suponer: está verificado hasta el 4 y `encrypt` rechaza por número
cualquiera más alto, porque la regla extrapolaría sin quejarse y una conjetura
sobre separación de claves es justo la que esta rama ya ha pagado dos veces.

Y el motivo de haber pedido cinco capturas en vez de una: **con el modificador 0
solo, esto parece ECB sin separación de claves ninguna**. El modificador 0 deja
la MFK intacta. Una implementación construida sobre esa única captura habría
ido y vuelto perfectamente y habría estado mal para todas las demás claves del
HSM.

Implementado en `FuturexMfkOperations`, 8 tests.

## 5. SafeNet — resuelto para lo capturado

Dos cosas varían por separado y confundirlas es toda la dificultad.

**El formato de clave** dice cómo se cifra: `11` es 3DES-ECB, `13` es 3DES-CBC
con IV cero. Nada más las diferencia — y por eso ECB y CBC con IV cero producen
**el mismo primer bloque**, siempre. Una comprobación a ojo de los primeros ocho
bytes no distingue los dos formatos. Hay un test que lo fija.

**La variante de la KM** dice para qué es la clave: un byte XOR-ado a los 24 de
la KM. Y es una **tabla, no una fórmula**:

| Variante | Constante |
|---|---|
| `00` DPK | `00` |
| `01` PPK | `28` |
| `07` KPV, DT | `18` |

No hay aritmética que lleve de `01 → 28` a `07 → 18`, así que las que faltan no
se deducen: se leen de la herramienta. `variant()` rechaza un código que no haya
visto en vez de dejar la KM intacta, porque «intacta» es ella misma una variante
válida (`00`) y una respuesta mal silenciosa sería indistinguible de una buena.

**La clave almacenada en el host** es `11` + código de formato + criptograma:
`1111…` para el formato 11 y `1113…` para el 13. Son dos puntos, no una ley.
Y no lleva la variante dentro: nada en una clave SafeNet almacenada dice para
qué sirve. Eso es un riesgo de interoperabilidad real, no una manía de este
banco.

Implementado en `SafeNetKmOperations`, 11 tests.

## 6. Thales Key Block AES — segundo vector

Misma KBPK, otra clave y **otra cabecera** (`10096B0AN00E0002`, con la `A` de
AES donde antes había una `T`). Se reproduce carácter por carácter. Dos vectores
bajo una misma KBPK son lo que separa «la derivación es correcta» de «la
derivación funciona para esta cabecera», porque la cabecera *es* el vector de
inicialización.

## 7. Atalla con clave simple — resuelto

La pestaña Lookup usaba otra MFK, `2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF`.
Con ella el bloque se reproduce entero:

```
1PUNE000,D3266EC69C61820019F4A9640A8F603DA14F78E154C7522D,55720A06F8964B8F
→ 0000000055556666   relleno 53 × 16   MAC válido
```

Dos MFK distintas, dos longitudes de clave distintas, el mismo algoritmo. El
formato Atalla queda cerrado salvo el desglose de la cabecera.

De paso, algo verificado y útil: **`KCV (V)` es el KCV estándar de seis dígitos
y `KCV (S)` son sus primeros cuatro.** Para `0000000055556666` el estándar da
`3BAFC4`, y la herramienta imprime `S: 3BAF` y `V: 3BAFC4`.

---

# Lo que hace falta capturar ahora

Queda muy poco, y lo primero es de un solo campo.

## A. Atalla: la clave triple, y poco más

Una **clave de 24 bytes** con la misma MFK y cabecera. Confirmaría por
observación lo que hoy sale por aritmética: que no lleva relleno.

La tabla de campos de la cabecera seguiría sin conocerse, y para eso el camino
barato no son más capturas a ciegas sino la pestaña **AKB Decode**: si desglosa
una cabecera campo a campo, un pantallazo vale por veinte bloques.

## C. SafeNet: el resto de la tabla de variantes

El desplegable **Variant** con la misma clave, la misma KM y formato 13, para
las variantes que queden (`02` a `06`, `08`…). Cada una es una fila de tabla que
no se puede deducir. Y **un formato distinto de 11 y 13** —el de longitud simple
o el triple— para confirmar que el prefijo `11` de la clave de host es fijo.

## D. Futurex: modificadores por encima de 4

Si el desplegable llega más allá, **el 5 y el 6**. Casi seguro que siguen la
regla, y «casi seguro» es exactamente lo que no vale. Dos capturas levantan el
límite.

## E. Thales Key Block: una KBPK AES de 128 bits

La derivación cambia el código de algoritmo y el número de bloques CMAC según la
longitud de la KBPK, y sólo está capturado el caso de 256 bits.

## F. Thales Key Block: un bloque con cabeceras opcionales

En los dos esquemas el IV es el primer bloque de la cabecera. Con cabeceras
opcionales no está observado si se extiende. Un `# Opt. KeyBlocks` distinto de
`00` lo resuelve.
