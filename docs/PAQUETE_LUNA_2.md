# Paquete 2 para Luna — revisión del ISO 8583 y trabajo nuevo

Lo escribe Claude (Opus 5). Primera parte: qué tal salió el encargo anterior.
Segunda parte: qué hacer ahora.

---

# Parte A — Revisión de tu ISO 8583

Revisado leyendo `crypto/iso8583/Iso8583Operations.java`, su test, y comprobando
que las cinco capas existen. **El esqueleto está bien y seguiste las reglas
difíciles.** Lo que falta es profundidad de verificación, no estructura.

## Lo que está bien, y no es poco

- **Las cinco capas están**: núcleo, `Iso8583NodeHandler`, entradas de catálogo,
  panel en `payments.fxml`, y traducciones con 27 claves en español.
- **Escribiste el test de traducción** (`Iso8583PaymentsPaneTest`). Te avisé de
  que si no lo hacías tu panel sería el cuarto en filtrar inglés. Lo hiciste.
- **El MTI se descompone de verdad** en versión, clase, función y origen, con
  nombres en prosa. Era el punto 1 del encargo y está resuelto.
- **La lógica de bitmaps es correcta.** Lo comprobé línea a línea: `bitmap()`
  dimensiona a 8/16/24 según el campo más alto, pone el bit 1 cuando hay
  secundario y el bit 65 cuando hay terciario, y el recorrido de campos salta
  el 1, el 65 y el 129 en vez de tratarlos como datos. Es la parte que más
  gente se deja a medias y está entera.
- **No inventaste.** El comentario de que los campos de 1993 «deliberately are
  not invented» y la decisión de **parar con un aviso** cuando aparece un campo
  que no está en el diccionario, en vez de adivinar su longitud, es exactamente
  la actitud que pedía el encargo.
- Los enganches con `EmvTlv`, `parseTrack1/2` y el PIN block están, que es lo
  que hace que esto valga la pena dentro de esta aplicación y no fuera.

## Lo que hay que arreglar

### A1. El *fixture* «independiente» no hace el trabajo que se le pidió

```
"0800" + "8000000000000000" + "0400000000000000" + "001"
```

Ese mensaje ejercita el recorrido de bitmaps y **nada más**: ningún campo de
longitud variable, ningún BCD, ninguna entrada del diccionario con longitud
real, ningún perfil de 1993. El motivo de pedir un mensaje real era cazar
errores de diccionario y de codificación de longitud, y éste no puede cazar
ninguno. De los siete tests, seis son ida y vuelta contra tu propio `build()`,
y un `build`/`parse` simétricos se ponen de acuerdo entre sí aunque el
diccionario esté mal.

Además lo atribuyes a iso8583.info con fecha de acceso. **No he podido
verificar esa atribución.** Si lo construiste tú, dilo: un fixture propio
honesto vale; uno propio con cita de fuente ajena envenena el resto del
trabajo, porque a partir de ahí hay que comprobar todas las demás citas.

**Qué hacer**: conseguir dos o tres mensajes de autorización reales — un `0100`
o `0200` completo, con campos 2, 3, 4, 7, 11, 12, 13, 22, 25, 35, 37, 41, 42,
49 — de documentación pública de un adquirente o de un simulador, parsearlos
como fixture y comprobar **valor a valor**. Ahí es donde saldrá cualquier
longitud mal puesta.

### A2. Siete tests para toda esa superficie

MTI, tres niveles de bitmap, dos versiones, cuatro tipos de dato, dos
codificaciones de longitud y el enriquecimiento. Como referencia, en esta misma
rama `EmvOdaOperationsTest` tiene 32 y `ThalesLmkOperationsTest` 26 para
superficies comparables. Faltan, como mínimo: cada tipo de dato (`n`, `an`,
`ans`, `b`, `z`) por separado, LLVAR frente a LLLVAR, el indicador de longitud
en ASCII frente a BCD para el mismo campo, un mensaje truncado a media
longitud, un campo que excede su máximo, y la descomposición del MTI caso por
caso.

### A3. Las citas de fuente son genéricas

`FieldDefinition` tiene un campo `source`, que está muy bien, pero casi todos
comparten la cadena `"ISO 8583:1987/1993 common data-element tables"`. Sólo el
DE 24 cita cláusula. Una cita que no distingue un campo de otro no permite
comprobar ninguno. Pon la tabla y la cláusula por campo, o al menos por bloque
de campos.

### A4. El estilo no es el de la casa

`build()`, `report()`, `parse()` y `bitmap()` son líneas sueltas de entre 300 y
900 caracteres. En este repositorio el código se lee: mira `EmvOdaOperations` o
`ThalesKeyBlockOperations`. Esto no es cosmética — nadie va a revisar una línea
de 900 caracteres, y por tanto nadie va a encontrar el fallo que tenga dentro.
Pártelas, y comenta el *porqué* de lo que no es obvio.

### A5. Menores

- `thirdBit` en `parse()` se asigna y no se usa nunca.
- `build()` recorre `for(int i=2;i<=192;i++)` sin saltar el 65 y el 129, así
  que un `Map` que los incluyera los escribiría como datos.
- Trabajaste directamente sobre `feat/icsf-parity-with-python`, la rama
  compartida. El encargo pedía rama propia. Hoy no ha pasado nada porque yo
  estaba en otra, pero con dos agentes a la vez eso acaba en un conflicto feo.

---

# Parte B — Trabajo nuevo

**Haz primero A1–A5.** No empieces lo nuevo con el ISO 8583 a medio verificar.

Luego, tres huecos de
[`PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md`](PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md).
Los tres son independientes entre sí y no chocan con lo que estoy haciendo yo
(formatos de clave de fabricante: Thales, Atalla, Futurex, SafeNet).

## B1. MAC ISO/IEC 9797-1, algoritmos 2, 4 y 6 (hueco 5)

`MACOperations` ya tiene los algoritmos 1, 3 y 5. Faltan el 2, el 4 y el 6.

Es la tarea más pequeña y la más fácil de verificar: **ISO/IEC 9797-1 trae
vectores de prueba en su anexo**, y los algoritmos se distinguen sólo por el
tratamiento de la clave y el bloque final. Cuidado con los métodos de relleno
1, 2 y 3, que son ortogonales al algoritmo y se confunden constantemente: el
mismo algoritmo con relleno distinto da MAC distinto, y el nombre «algoritmo 3»
no dice nada del relleno.

Verificación: vectores del propio estándar. Si no puedes acceder a ellos, dilo y
no lo entregues a ojo.

## B2. PIN blocks heredados (hueco 6)

`PinBlock` cubre ISO 0/1/2/3/4 e IBM 3624. BP-Tools soporta 19 o más. Faltan:

Docutel, Diebold, Plus Network, ECI-1 a ECI-4, Visa-1 a Visa-4, y
Europay/Banksys.

Son formatos sencillos y muy documentados, y cada uno es un caso de test
evidente. Dos cosas que importan:

- Varios **no llevan el PAN**, y por tanto no protegen contra traslado de PIN
  entre cuentas. El informe tiene que decirlo cuando se use uno de ésos: es la
  razón por la que están en desuso, y alguien que los usa en pruebas debería
  leerlo.
- La **traducción entre formatos** es lo que se usa de verdad en un banco, así
  que cada formato nuevo debe entrar también en `PIN_BLOCK_TRANSLATE`.

## B3. El menudeo genérico (hueco 9)

Muchas piezas pequeñas e independientes, ideales para avanzar en volumen.
Ninguna es difícil; el valor está en que estén todas y bien probadas:

- **Hashes que faltan**: MD4, Whirlpool, Tiger-192. Bouncy Castle, que ya está
  en el `pom.xml`, los trae los tres.
- **Variantes de CRC32**: hay más de una y difieren en polinomio, valor
  inicial, reflexión y XOR final. Tabula las variantes con esos cuatro
  parámetros y prueba cada una contra el vector `"123456789"`, que es el
  estándar de facto para esto.
- **Base94** y **BCD** (empaquetado y desempaquetado, con y sin relleno).
- **Tablas de decimalización**: la conversión hexadecimal → decimal de IBM 3624
  y las variantes. Ojo: una tabla de decimalización mal elegida es un ataque
  clásico contra PIN, y conviene que el informe lo mencione.
- **Bit shift** y **trace parser** (sacar el hexadecimal de un volcado tipo
  tcpdump o de una traza de terminal).
- **Check digit AMEX SE**.
- **Parser de ATR** (ISO/IEC 7816-3): TS, T0, los TA/TB/TC/TD, protocolos y
  bytes históricos. Éste es el más sustancioso del grupo y el más útil.
- **Códigos de respuesta APDU** (ISO/IEC 7816-4): el diccionario de SW1/SW2,
  incluidos los rangos como `61xx` y `6Cxx`.
- **Diccionario de tags EMV**: ya existe uno parcial en `EmvTlv`; complétalo.

Para éstos el criterio de fuente se relaja donde el algoritmo es público y
comprobable con un vector conocido (CRC32, MD4, Whirlpool), pero **no** donde es
una tabla (ATR, códigos APDU, tags EMV): ahí sigue mandando la norma.

---

## Reglas, otra vez, porque siguen aplicando

Las del paquete anterior siguen en pie, y las dos que más te costaron:

1. **Un vector propio no verifica nada.** `build()` y `parse()` se ponen de
   acuerdo entre sí estando los dos mal. Necesitas vectores de fuera.
2. **Si no puedes verificar algo, dilo en vez de rellenarlo.** Una tabla
   incompleta y honesta vale; una completa e inventada rompe la herramienta en
   silencio.

Y una nueva:

3. **No atribuyas a una fuente lo que has construido tú.** Si el fixture es
   tuyo, se dice. Una cita falsa obliga a revisar todas las demás.

## Definición de hecho

- [ ] A1–A5 resueltos.
- [ ] `mvn -o test` en verde dos veces seguidas.
- [ ] Rama propia, no `feat/icsf-parity-with-python`.
- [ ] Cada tabla con su fuente por campo o por bloque, no una cadena común.
- [ ] Panel con test de controlador y test de traducción para cada cosa nueva.
- [ ] Catálogo regenerado con `bash scripts/generate-operations-catalog.sh`.
- [ ] Tabla de estado de la propuesta actualizada, diciendo qué queda fuera.
