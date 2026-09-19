# Paquete de trabajo para Luna — ISO 8583 en CryptoCarver

Documento de encargo. Lo escribe Claude (Opus 5), que trabaja en paralelo en la
misma rama sobre otra cosa. Léelo entero antes de tocar nada: la sección 4 no es
relleno, es la que decide si el trabajo sirve.

---

## 0. El encargo en una línea

Implementar **ISO 8583** en CryptoCarver: parseo y construcción de mensajes,
bitmaps, diccionario de campos y tipos de dato, con panel propio y nodos de
Process Designer, al nivel de calidad y verificación del resto del repositorio.

---

## 1. Por qué esta tarea y no otra

El repositorio tiene una lista de huecos frente a BP-Tools en
[`docs/PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md`](PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md).
ISO 8583 es el hueco número 4 y está entero sin hacer.

Se te asigna éste y no los formatos de clave de fabricante (Thales Key Block,
Atalla AKB, Futurex MFK, SafeNet KM) por una razón concreta, y conviene que la
tengas presente todo el rato:

> **Un parser de mensajes que se equivoca se nota. Un envoltorio de claves que se
> equivoca no se nota.**

Si parseas mal un campo, el resultado es visiblemente absurdo y alguien lo ve. Si
cifras una clave bajo la variante equivocada, sale un bloque hexadecimal de
aspecto perfecto que nadie puede distinguir del correcto hasta que el HSM lo
rechaza tres semanas después. Ese trabajo se queda con quien puede contrastar
fuentes primarias caso por caso. El tuyo es grande pero benigno: aprovéchalo y
hazlo a fondo.

---

## 2. Dónde trabajar

- Repositorio: `/Users/feliperodriguezfonte/dev/CryptoCarver`
- Rama base: **`feat/eidas2-eudi-wallet`**
- **Abre tu propia rama o worktree a partir de ella.** No trabajes directamente
  sobre la rama compartida: yo estoy comiteando en ella hoy mismo.
- Compilar: `mvn -o -q compile`
- Suite completa: `mvn -o test` — ahora mismo **1848 tests, 0 fallos**. Si al
  terminar no está en verde, no está hecho.
- Tests de UI etiquetados: `mvn -o test -Dtest=LoQueSea -DrunUiTests=true`

---

## 3. Qué construir

### 3.1 Núcleo (obligatorio)

Una clase `com.cryptocarver.crypto.Iso8583Operations` (o un paquete
`crypto/iso8583/` si se te va de tamaño, que es probable) que cubra:

1. **MTI** — los cuatro dígitos, y su descomposición en versión, clase, función
   y origen. Que el informe diga qué es un `0100` en palabras, no sólo que es
   `0100`.
2. **Bitmaps** — primario, secundario y terciario. El bit 1 del primario indica
   presencia de secundario; ojo con el terciario, que casi nadie usa bien.
   Soporta bitmap en binario y en hexadecimal ASCII, porque en la vida real
   llegan de las dos formas y confundirlas es el error más común del mundo.
3. **Diccionario de campos** para ISO 8583 **:1987** y **:1993** — número,
   nombre, tipo de dato y longitud. Las dos versiones difieren en varios campos y
   hay que poder elegir.
4. **Tipos de dato y longitud**: `n`, `an`, `ans`, `b`, `z`, fijos y variables
   (`LLVAR`, `LLLVAR`), con el indicador de longitud en BCD o en ASCII según el
   perfil. Esto es donde se pierde la gente: el mismo mensaje se parsea distinto
   según cómo esté codificada la longitud, y el parser tiene que decir bajo qué
   suposición ha trabajado.
5. **Parseo y construcción**, simétricos y probados uno contra otro.
6. **Informe legible** al estilo del resto del repositorio: campo a campo, con
   el nombre, el tipo declarado, la longitud y el valor, y avisos cuando algo no
   cuadra con el diccionario.

### 3.2 Enganches con lo que ya existe (obligatorio, y es lo que lo hace valioso)

- **Campo 55** (ICC data) es EMV TLV. Ya existe
  `com.cryptocarver.crypto.EmvTlv` con parser y diccionario de tags: llámalo, no
  reescribas uno.
- **Campos 35 y 45** son track 2 y track 1. Ya existe
  `PaymentOperations.parseTrack2`.
- **Campo 52** es el PIN block. Ya existe `PaymentOperations` con los formatos
  ISO 0–4.
- Un mensaje parseado que además desmenuza el 55 con el diccionario EMV es
  exactamente lo que no tiene ninguna herramienta web suelta, y es la razón de
  hacerlo aquí dentro.

### 3.3 Fuera de alcance en esta entrega

No los toques, y déjalos escritos como pendientes:

- ATM NDC, Wincor, AS2805, APACS30 — son dialectos, van después.
- Perfiles de esquema completos de Visa y Mastercard. Puedes dejar la
  arquitectura preparada para perfiles (el diccionario ya debería ser
  parametrizable), pero no inventes tablas de campos privados de esquema: son
  propietarias y no vas a poder verificarlas.

---

## 4. Las reglas de la casa

Esto es lo que diferencia un aporte útil de uno que hay que revisar entero.

### 4.1 Fuentes primarias, y decirlo

**No escribas de memoria una tabla de campos.** Ni una. Si el campo 3 es
`n 6` "Processing Code", que sea porque lo has mirado, y que el Javadoc diga
dónde. Cita la norma y la cláusula igual que hacen las clases existentes:
mira `EmvOdaOperations` (cita EMV Book 2 por cláusula) o
`EidasCertificateInspector` (cada *finding* lleva el identificador de requisito
de la especificación).

### 4.2 La advertencia, con nombre y apellidos

Hoy mismo, en este repositorio, implementando el esquema de variante de LMK de
Thales:

- Dos descripciones muy citadas del algoritmo ponían el XOR de la variante en
  **bytes distintos**. Las dos estaban mal.
- Una tercera fuente, un proyecto en Go, acertaba — pero no había forma de
  saberlo sin el manual.
- Sólo el ejemplo resuelto del manual del fabricante lo decidió.
- Y el manual **también tenía dos errores**: una constante mal impresa en una
  cláusula que se contradice con otra, y unos componentes de ejemplo que no
  forman la clave que esa misma página dice.

Lo demoledor: **cifrar y descifrar se ponían de acuerdo entre sí aunque los dos
aplicaran la variante al byte equivocado**. Un round-trip verde no probaba nada.

Aplícalo a lo tuyo: un mensaje que construyes y vuelves a parsear tú mismo te
dará verde con un diccionario inventado. Necesitas **mensajes reales de
referencia**, no sólo simetría.

### 4.3 Los tests son la documentación

Mira `src/test/java/com/cryptocarver/crypto/ThalesLmkOperationsTest.java` o
`EmvOdaOperationsTest.java` para el tono. Los nombres de test son frases, y los
comentarios explican **por qué importa el caso**, no qué hace la línea. Cuando
encuentres un error en una norma o en un ejemplo publicado, déjalo como test que
documenta la discrepancia, no lo corrijas en silencio.

### 4.4 Material sensible

`docs/LAB_VS_PRODUCTION.md` marca la frontera. Esto es un banco de pruebas para
datos de prueba. Los PAN y los PIN blocks son material sensible: en Process
Designer van como `transientSecrets` y nunca se persisten en `.cfprocess.json`.
Mira cómo lo hacen los nodos de pago existentes (`secret(...)` en los
descriptores).

### 4.5 Bilingüe, de verdad

La aplicación es español/inglés. **El inglés se ha colado en la ventana española
tres veces**, siempre igual: alguien escribe los textos directamente en el FXML y
no los da de alta en el catálogo. Hay guardas que lo impiden — mira
`src/test/java/com/cryptocarver/ui/EmvOdaPaneTranslationTest.java` y el test
`everySentenceThePaneShowsIsTranslatable` en `ThalesLmkPaneTest`. **Escribe el
equivalente para tu panel.** Si no lo haces, tu panel será el cuarto.

---

## 5. Arquitectura: dónde va cada cosa

El patrón está consolidado. Cinco capas, y las cinco hacen falta:

| Capa | Dónde | Ejemplo a copiar |
|---|---|---|
| Núcleo | `src/main/java/com/cryptocarver/crypto/` | `EmvOdaOperations.java` |
| Nodos de Process Designer | `src/main/java/com/cryptocarver/model/process/handlers/` | `PaymentOperationsNodeHandler.java` |
| Catálogo de operaciones | `src/main/java/com/cryptocarver/model/OperationRegistry.java` | las entradas `op_pay_*` |
| Interfaz | `src/main/resources/fxml/` + controlador en `ui/` | la sección "Offline Data Authentication" en `emv.fxml` + `EMVController` |
| Traducciones | `ui/ModuleTextCatalog.java` + `resources/i18n/messages*.properties` | cualquiera de las anteriores |

Para ISO 8583 lo natural es un **handler propio** (`Iso8583NodeHandler`) y no
meterlo en el de pagos, que ya tiene 34 tipos de nodo. Y un **módulo propio de
UI** si crece, o una sección en el de pagos si no.

---

## 6. Trampas del repositorio

Todas éstas me han mordido a mí esta semana. Te las ahorro:

1. **`docs/OPERATIONS_CATALOG.md` se genera.** Lo produce entero
   `OperationRegistry.generateCatalogMarkdown()` y hay un test que compara. Si
   escribes a mano en ese fichero, lo pierdes. Edita el generador y ejecuta
   `bash scripts/generate-operations-catalog.sh`.
2. **Los alias roban nombres globalmente.** En `UiNavigationRegistry`, registrar
   un alias corto y genérico se lo quita al módulo que debería tenerlo. Registrar
   AdES con el alias "PAdES" hizo que navegar a PAdES abriera la cartera. Usa
   nombres que sólo puedan ser tuyos.
3. **Toda operación registrada necesita una ruta de navegación que llegue a un
   panel real.** Hay dos tests que lo exigen (`UiNavigationRegistryTest` y
   `ModernMainControllerUITest`). Si registras la operación antes de tener
   panel, la suite se pone roja.
4. **Barras de acción de 4 o más botones deben ser `FlowPane` con
   `styleClass="responsive-action-bar"`**, no `HBox`. Hay un gate de FXML que lo
   comprueba.
5. **`PaymentOperationsNodeHandlerTest` tiene el número de tipos de nodo a
   fuego** (`assertEquals(34, ...)`). Si añades tipos ahí, actualízalo. Si haces
   handler propio, no te afecta.
6. **Todo tipo de nodo debe rechazar una configuración vacía** — hay un test que
   recorre todos y lo exige.
7. **`I18nService` guarda los listeners de idioma con referencias débiles** y lo
   documenta: quien llama tiene que quedarse la *binding* de `ModuleI18n.bind`
   en un campo. Tirar el valor devuelto hace que el refresco de idioma funcione o
   no según haya pasado el recolector de basura. Eso causó un test intermitente
   que parecía otra cosa durante días (commit `f17b953`).
8. **Los `fx:id` se cablean por nombre y fallan en silencio.** Un nombre que no
   coincide deja un `null` y un botón que no hace nada. Escribe un test de
   controlador que pulse los botones por reflexión — mira `EmvOdaControllerTest`
   o `ThalesLmkPaneTest`.

---

## 7. Colisiones conmigo

Yo estoy trabajando hoy en **Thales Key Block** (esquema S), en la misma rama
base. Ficheros que tocaremos los dos:

- `src/main/java/com/cryptocarver/model/OperationRegistry.java` — una línea cada
  uno, y el generador de markdown.
- `src/main/resources/i18n/messages*.properties` — los dos añadimos al final.
- `src/main/java/com/cryptocarver/ui/ModuleTextCatalog.java`.
- `docs/PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md` — la tabla de estado.
- `docs/OPERATIONS_CATALOG.md` — **generado**: no lo resuelvas a mano, regenera
  después de fusionar.

Son todos añadidos al final de listas, así que los conflictos serán mecánicos.
Si haces handler propio en vez de tocar `PaymentOperationsNodeHandler`, evitamos
el único fichero donde el conflicto sería molesto de verdad.

---

## 8. Definición de hecho

- [ ] `mvn -o test` en verde, con la suite completa, **dos veces seguidas** (hay
      un par de tests de UI intermitentes conocidos; si te salta uno, mira si es
      de los tuyos antes de dar nada por bueno).
- [ ] Cada tabla y cada constante con su fuente citada en el Javadoc.
- [ ] Al menos un **mensaje real de referencia** parseado en un test, no sólo
      round-trips contra ti mismo.
- [ ] Nodos de Process Designer con descriptores y validación.
- [ ] Panel con su test de controlador y su test de traducción.
- [ ] Catálogo regenerado con el script.
- [ ] Tabla de estado de la propuesta actualizada, diciendo también **qué has
      dejado fuera y por qué**.

Y lo último, que vale por todo lo anterior: **si no puedes verificar algo, dilo
en vez de rellenarlo**. Una tabla incompleta y honesta vale; una completa e
inventada rompe la herramienta en silencio y se descubre tarde.
