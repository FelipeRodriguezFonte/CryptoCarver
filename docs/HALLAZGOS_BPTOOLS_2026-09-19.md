# Lo que salió de las capturas de BP-Tools del 19-09-2026

Seis capturas de BP-Tools Cryptographic Calculator 21.06. Las cinco preguntas
abiertas quedaron cerradas, cuatro de ellas en la primera pasada. Este documento
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

### Lo que sigue sin saberse

- **El desglose de la cabecera.** `1PUNE000` se lee como ocho caracteres con un
  dígito de versión delante y nada más. No hay fuente verificada para la tabla
  de campos y adivinarla produciría un validador que rechaza bloques buenos.
- **La regla de relleno.** El vector mete una clave de 16 bytes en un campo de
  24, y los ocho sobrantes salen como `44` repetido — constante, no aleatorio.

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

Fijado en `ThalesLmkBpToolsVectorTest`.

## 4. Futurex — resuelto para el modificador 0

Con modificador 0, el cifrado es **3DES-ECB liso bajo la MFK**, sin variante.
Confirmado con las dos MFK que la herramienta trae:

| MFK | Criptograma de `0123456789ABCDEFFEDCBA9876543210` |
|---|---|
| `D2DE5CD9110F4CAB1111111111111111` | `786DCE0EE3CB07CF5D590C65C17A0E29` |
| `…0123456789ABCDEF` (triple) | `F0700DDBFB49DDD5A3280E65263A6EED` |

Sin implementar todavía: un formato de protección de clave cuyo único caso
conocido es «sin variante» no es un formato, es un ECB. Lo que hace falta es la
tabla de modificadores.

## 5. SafeNet — resuelto para la variante 00

Con formato 11 (doble longitud DES3, ECB) y variante 00 (DPK), la KM se usa tal
cual y el cifrado es **3DES-ECB liso**: `968F5C677725C7C4E31E3E4C7ACA58B9`. La
herramienta lo dice ella misma — imprime «KM (variant applied)» idéntica a la KM.

La clave almacenada en el host sale como `1111` + criptograma. El formato es
`11`, así que los cuatro caracteres no son formato + variante (sería `1100`).
Con un solo caso no se distingue entre «el formato va duplicado» y otra cosa.

---

# Lo que hace falta capturar para cerrar el resto

Todas son la misma pantalla cambiando un desplegable. Valores de la casa:
clave doble `0123456789ABCDEFFEDCBA9876543210`, triple
`0123456789ABCDEF8080808080808080FEDCBA9876543210`.

## A. Atalla: la regla de relleno y la cabecera

1. **Misma MFK y cabecera `1PUNE000`, clave de 8 bytes** `0123456789ABCDEF`.
   Dice si el campo sigue midiendo 24 bytes y si el relleno sigue siendo `44`.
2. **Lo mismo con la clave triple de 24 bytes.** Sin sitio para relleno; confirma
   que el campo es del tamaño de la clave y no fijo.
3. **Tres cabeceras distintas con la misma clave**, cambiando un carácter cada
   vez: `1PUNE000`, `1PUNE100`, `1SUNE000`. Con eso deduzco qué posiciones son
   campos y cuáles están fijas, sin inventarme la tabla.

Y si la pestaña **AKB Decode** acepta un bloque y explica la cabecera campo a
campo, un pantallazo de eso vale por las tres.

## B. Futurex: la tabla de modificadores

El desplegable **Modifier** con la misma clave y la misma MFK, para los valores
**1, 2, 3 y 4**. Cuatro criptogramas de la misma clave bajo la misma MFK aíslan
la variante limpiamente. Con eso se implementa.

## C. SafeNet: formato y variante

1. **Misma clave y KM, variante distinta de 00** (la siguiente de la lista).
2. **Misma clave y variante 00, formato distinto** (el de longitud simple o el
   triple).

Con esos dos se separa qué parte del prefijo `1111` es el formato y qué parte la
variante, y qué hace la variante a la KM.

## D. Thales Key Block AES: una KBPK de 128 bits

La derivación implementada cambia el código de algoritmo y el número de bloques
CMAC según la longitud de la KBPK, y sólo se capturó el caso de 256 bits. Una
captura con una **KBPK AES de 16 bytes** confirma los otros dos caminos. Es la
misma pantalla cambiando el campo AES KBPK.

## E. Thales Key Block: un bloque con cabeceras opcionales

En los dos esquemas el IV es el primer bloque de la cabecera. Cuando hay
cabeceras opcionales, no está observado si el IV se extiende o se queda en el
primer bloque. El código toma el primer bloque y lo dice. Un bloque con un
`# Opt. KeyBlocks` distinto de `00` lo resuelve.
