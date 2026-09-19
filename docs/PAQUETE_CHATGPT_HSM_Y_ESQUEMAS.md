# Paquete para ChatGPT — banco de comandos HSM, secure messaging y tokenización

Lo escribe Claude (Opus 5). Somos tres trabajando sobre CryptoCarver: tú, Luna
y yo. Este documento es tu encargo completo — **implementación de punta a punta,
las cinco capas**, no sólo el núcleo.

Lee la sección 3 antes de escribir código. Es la que separa un aporte que sirve
de uno que hay que revisar entero.

---

## 1. Quién hace qué, y dónde no pisar

| Quién | Qué | Ficheros suyos |
|---|---|---|
| **Luna** | ISO 8583, MAC ISO 9797-1 algoritmos 2/4/6, PIN blocks heredados, menudeo genérico | `crypto/iso8583/`, `MACOperations`, `PinBlock`, `PaymentOperations` |
| **Claude** | Formatos de protección de clave de fabricante (Thales LMK y Key Block hechos; Atalla AKB, Futurex MFK, SafeNet KM pendientes), extracción de vectores, revisión | `ThalesLmkOperations`, `ThalesKeyBlockOperations`, `TR31*` |
| **Tú** | Lo de este documento | Todo nuevo |

Rama base: **`feat/eidas2-eudi-wallet`**. **Abre rama propia o worktree.** Luna
trabajó sobre una compartida y por suerte no coincidimos; con tres a la vez eso
acaba mal.

Ficheros que tocaremos los tres, todos por añadido al final de listas:
`OperationRegistry.java`, `ModuleTextCatalog.java`, `messages*.properties`,
`UiNavigationRegistry.java` y la tabla de estado de
[`PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md`](PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md).
`docs/OPERATIONS_CATALOG.md` **se genera**: no lo resuelvas a mano, regenera con
`bash scripts/generate-operations-catalog.sh`.

Comprobación: `mvn -o test`. Ahora mismo **1909 tests, 0 fallos**. Si al acabar
no está en verde, no está hecho.

---

## 2. El encargo

Tres bloques, de la lista de huecos frente a BP-Tools. En este orden.

### A. Banco de comandos host de HSM — hueco 7, el grande

**Es el diferenciador de KeyLab y no tenemos nada.** Hoy sólo hay una caché de
HSM simulada de sesión.

Construir un banco que componga y descomponga mensajes de comando de host de
payShield: la trama con su cabecera, el código de comando, los campos en orden,
y la respuesta con su código de error. Comandos mínimos, que son los que se usan
a diario:

- `A0` generar clave, `A6` importar clave, `A8` exportar clave
- `BU` generar valor de comprobación de clave
- `CA` traducir bloque de PIN de una ZPK a otra
- `CC` traducir bloque de PIN de LMK a ZPK
- `CW` generar CVV, `CY` verificar CVV
- `DC` verificar PIN (IBM 3624), `EC` verificar PIN (Visa PVV)
- `FA` traducir una ZPK de ZMK a LMK
- `NC` diagnóstico
- `M0`–`M6` cifrado y MAC de datos

Fuente primaria: **payShield 10K Host Programmer's Manual**, que está público en
*payShield 10K Host Programmer's Manual*, documento 007-001518-023 v2.3a.
Cítalo así, por título, número y cláusula. No pongas una URL en un comentario
del código: la copia que circula está en un servidor de terceros que puede
desaparecer, y una cita que no resuelve es peor que ninguna.
(descárgalo y pásalo por `pdftotext -layout`; yo lo he usado hoy para el Key
Block y los capítulos están limpios). El detalle campo a campo de cada comando
está en el *Core Host Commands reference manual*, que es otro documento — si no
lo consigues, **implementa sólo los comandos cuyos campos puedas verificar y di
cuáles has dejado fuera**.

Lo valioso no es enviar tramas: es **descomponer una que alguien capturó** y
decir qué pide, qué campos lleva y qué contesta el HSM, con el código de error
traducido. Eso es lo que hace falta a las tres de la mañana.

Nada de red: esto compone y analiza tramas, no abre sockets. El repositorio es
local por diseño; mira `docs/LAB_VS_PRODUCTION.md`.

### B. Secure messaging EMV por esquema — hueco 3

Hay un `generateScriptMAC` genérico en `EMVOperations` y faltan los perfiles de
esquema. Implementar el cifrado de PIN en scripts de emisor y el MAC según
**Visa** y según **Mastercard**, que difieren en el relleno, en la derivación de
la clave de sesión y en qué entra en el MAC.

Aquí hay un detalle que se lleva por delante a mucha gente: el MAC de un script
se calcula sobre la cabecera del comando APDU **más** los datos, y qué parte
exactamente depende del esquema. Si lo haces con un solo perfil y lo llamas
genérico, estará mal para uno de los dos.

### C. HCE y tokenización — hueco 8

- **Visa**: LUK, MSD, qVSDC
- **Mastercard**: CVC3 y PIN-CVC3, y **DS** (DSPK, DS Summary, DS Digest)
- ICC Dynamic Number
- Token **CAP** / SecureCode
- **AMEX CSC v1 y v2**

Es el bloque con más piezas y el que más se beneficia del método de la sección
3, porque casi todo esto lo calcula BP-Tools y casi nada está bien documentado
en abierto.

---

## 3. El método que hace que esto funcione: BP-Tools como oráculo

**Esto es lo más importante del documento.**

Felipe tiene **BP-Tools 21.06** (Cryptographic Calculator y HSM Commander) en
una máquina Windows. Es la herramienta con la que buscamos paridad. Puede
generar cualquiera de estos valores con entradas que tú elijas, y ese par
entrada/salida es un vector de prueba.

Hoy mismo cerré con ese método el Key Block de Thales, que llevaba bloqueado
dos intentos. El manual del fabricante describe los algoritmos pero **nunca dice
cómo salen de la LMK las claves de cifrado y de MAC** — las cláusulas 8.6 y 8.7
dicen sólo «una variante de la LMK». Pedirle a BP-Tools que envolviera una clave
bajo la LMK de test publicada y leer lo que derivó lo resolvió en diez minutos:

```
KBEK = KBPK XOR 45..45      KBAK = KBPK XOR 4D..4D
```

Y de paso resolvió una segunda ambigüedad que yo no habría acertado adivinando:
el autenticador cubre la cabecera más los datos **cifrados**, no los claros. Con
los claros sale `C033654B`; el hardware dice `31D00034`.

**Cómo usarlo**: cuando llegues a algo que no puedas verificar con una fuente
pública, **no lo implementes**. Escribe en el informe exactamente qué entradas
necesitas que Felipe meta en BP-Tools y qué campos quieres que te copie. Un
mensaje suyo con un par entrada/salida vale más que una tarde de deducción, y no
se equivoca.

Ojo con una cosa: **usar la herramienta para que calcule es usarla para lo que
es.** Descompilarla no, ni hace falta.

---

## 4. Las reglas de la casa

### 4.1 Fuentes primarias, citadas por cláusula

Mira `EmvOdaOperations` (cita EMV Book 2 por cláusula) o
`ThalesLmkOperations`. Cada constante y cada tabla llevan de dónde salen.

### 4.2 Por qué, con tres ejemplos de hoy

**Las fuentes secundarias mienten.** Implementando el esquema de variante de
Thales, dos descripciones muy citadas ponían el XOR en bytes **distintos**, y
las dos estaban mal. Sólo el ejemplo resuelto del manual del fabricante lo
decidió. Y el manual tenía **dos erratas propias**: una constante que se
contradice con otra cláusula, y unos componentes de ejemplo que no forman la
clave que esa misma página declara.

**Un round-trip verde no prueba nada.** Cifrar y descifrar se ponen de acuerdo
entre sí aunque los dos apliquen la variante al byte equivocado. Hoy encontré un
bug real en el TR-31 que ya enviábamos —el campo de longitud de un bloque
opcional se leía como contador de bytes de datos en vez de como longitud del
bloque entero— y llevaba ahí sin que nadie lo viera porque **todos los tests
envolvían y desenvolvían con el mismo código**. Lo cazó un bloque generado por
otra implementación, a la primera. Consecuencia del bug: cualquier key block con
bloques opcionales era ilegible para nosotros y los nuestros ilegibles para los
demás.

**Cuidado con los bits de paridad.** Cambiar el último bit de un byte de una
clave DES no cambia la clave: DES ignora la paridad. Es la forma obvia de
escribir «rechaza la clave equivocada» y no funciona.

### 4.3 Si no puedes verificarlo, dilo

Una tabla incompleta y honesta vale. Una completa e inventada rompe la
herramienta en silencio y se descubre tarde, que en formatos de clave es el peor
resultado posible. Cuando dejes algo fuera, déjalo escrito en la tabla de estado
de la propuesta con el motivo.

### 4.4 Material sensible

`docs/LAB_VS_PRODUCTION.md` marca la frontera. Claves, PAN y PIN blocks son
material sensible: en Process Designer van como `transientSecrets` y no se
persisten en `.cfprocess.json`. Mira cómo lo hacen los nodos existentes
(`secret(...)` en los descriptores).

### 4.5 Bilingüe, de verdad

La aplicación es español/inglés y **el inglés se ha colado en la ventana
española cuatro veces**, siempre igual: alguien escribe los textos en el FXML y
no los da de alta en el catálogo. Hay guardas que lo impiden: mira
`src/test/java/com/cryptocarver/ui/ThalesKeyBlockPaneTest.java`, método
`everySentenceThePaneShowsIsTranslatable`. **Escribe el equivalente para cada
panel que hagas.**

---

## 5. Las cinco capas

Las cinco, para cada cosa. Una sin panel no la puede usar nadie; una sin nodo no
se puede automatizar.

| Capa | Dónde | Ejemplo a copiar |
|---|---|---|
| Núcleo | `crypto/` | `ThalesKeyBlockOperations.java` |
| Nodos de Process Designer | `model/process/handlers/` | `PaymentOperationsNodeHandler.java` |
| Catálogo | `model/OperationRegistry.java` | las entradas `op_pay_*` |
| Interfaz | `resources/fxml/` + controlador en `ui/` | la sección «Thales Key Block» de `keys.fxml` |
| Traducciones | `ui/ModuleTextCatalog.java` + `resources/i18n/messages*.properties` | cualquiera |

Para el bloque A lo natural es **módulo propio** y **handler propio**: el de
pagos ya tiene 36 tipos de nodo.

---

## 6. Trampas del repositorio

Todas me han mordido a mí esta semana:

1. **`docs/OPERATIONS_CATALOG.md` se genera** y hay un test que lo compara.
2. **Los alias roban nombres globalmente.** Registrar AdES con el alias `PAdES`
   hizo que navegar a PAdES abriera la cartera. Usa nombres que sólo puedan ser
   tuyos, y no registres siglas cortas.
3. **Toda operación registrada necesita una ruta de navegación que llegue a un
   panel real**, o dos tests se ponen rojos.
4. **Barras de 4 o más botones deben ser `FlowPane` con
   `styleClass="responsive-action-bar"`**, no `HBox`. Hay un gate de FXML.
5. **`PaymentOperationsNodeHandlerTest` tiene el número de tipos a fuego.** Si
   haces handler propio, no te afecta.
6. **Todo tipo de nodo debe rechazar configuración vacía**; hay un test que los
   recorre todos.
7. **`I18nService` guarda los listeners de idioma con referencias débiles**:
   quien llama tiene que quedarse la *binding* de `ModuleI18n.bind` en un campo.
   Tirarla hace que el refresco de idioma funcione o no según el recolector de
   basura. Eso causó un test intermitente que parecía otra cosa durante días.
8. **Los `fx:id` se cablean por nombre y fallan en silencio.** Escribe un test de
   controlador que pulse los botones por reflexión: mira `ThalesKeyBlockPaneTest`.
9. Hay **dos tests de UI con fallos intermitentes conocidos**
   (`IcsfModuleI18nTest`, `ModernMainControllerFormatNormalizationTest`) y algún
   *timeout* de JavaFX bajo carga. Si te salta uno, comprueba si es tuyo antes
   de dar nada por bueno, y repite la suite.

---

## 7. Definición de hecho

- [ ] `mvn -o test` en verde **dos veces seguidas**.
- [ ] Rama propia.
- [ ] Cada tabla y cada constante con su fuente citada por cláusula.
- [ ] **Al menos un vector externo por bloque** — de la norma, de otra
      implementación, o de BP-Tools vía Felipe. No valen sólo round-trips.
- [ ] Las cinco capas para cada cosa.
- [ ] Panel con test de controlador y test de traducción.
- [ ] Catálogo regenerado con el script.
- [ ] Tabla de estado de la propuesta actualizada, **diciendo qué has dejado
      fuera y por qué**.
- [ ] Una lista de los vectores que necesitas de BP-Tools, si los necesitas.
