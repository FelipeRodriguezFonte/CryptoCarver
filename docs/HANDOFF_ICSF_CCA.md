# Key tokens ICSF / CCA — arquitectura y decisiones

Análisis de los *key tokens* nativos del coprocesador criptográfico de host de
IBM z/OS (ICSF / CCA), portado desde la herramienta en Python
`icsf_tokens.py` + `icsf_batch.py`.

Fuente de contraste: *z/OS ICSF Application Programmer's Guide*
(`csfb400_icsf_apg_hcr77e0`), apéndices B y C. Las referencias de página que
aparecen en el código son de la página **impresa** del manual (la física del PDF
es +54).

---

## 1. Dónde vive

| Capa | Ubicación |
|---|---|
| Núcleo | `com.cryptocarver.crypto.icsf` (sin dependencias de interfaz) |
| Interfaz | `IcsfTokenController` + `IcsfBatchController` en `com.cryptocarver.ui` |
| Vistas | `src/main/resources/fxml/icsf_token.fxml`, `icsf_batch.fxml` |
| Textos | `icsf.*` en los tres bundles; slice `ModuleTextCatalog.icsf()` |
| CLI | `icsf-token`, `icsf-batch` en `CryptoCarverCli` |
| Tests | `src/test/java/com/cryptocarver/crypto/icsf/` y `.../ui/Icsf*` |

**No es un módulo de navegación propio.** Los dos paneles son `fx:include`
autocontenidos dentro de `keys.fxml`, siguiendo el patrón de
`pkcs11_profiles.fxml`: FXML propio, `fx:controller` propio, slice de textos
propio y espacio de nombres i18n propio. `KeysController` solo aporta cuatro
campos `@FXML`, el reenvío del `StatusReporter` y una rama en
`expandSymmetricPane`; **no contiene lógica ICSF**.

En el árbol de operaciones aparecen bajo **Keys → Tools**, junto a Key Material
Inspector y KeyStore Inspector, que son la misma clase de herramienta.

### Por qué separados de TR-31

TR-31 y los key tokens nativos de CCA son formatos distintos, los manejan
servicios distintos (`CSNBKEX` / `CSNBKIM` frente a export/import TR-31) y no
deben leerse como una sola funcionalidad. En este repositorio la unidad de
separación no es el módulo: es el panel + controlador + espacio de nombres.
`keys.fxml` ya tiene TR-31, TR-34, AES Key Wrap y RSA Key Exchange como paneles
que no se mezclan entre sí; ICSF entra igual.

---

## 2. La decisión de diseño que gobierna todo lo demás

El original en Python guarda **prosa en español** dentro del resultado, y la capa
de lote la vuelve a leer para decidir:

```python
fila["ambito"]   = _primera_palabra(s.get("Ambito", ""))  # "INTERNO (X'01') ..." -> "INTERNO"
fila["material"] = _material_corto(...)                   # busca "en claro" / "cifrad"
if label.startswith("SIMPLE"): ...
if fila["material"] == "EN CLARO": out.append(...)        # hallazgo por comparar un string español
```

Traducir la ficha al inglés rompe **cada una** de esas comprobaciones: el lote
dejaría de detectar `MATERIAL-EN-CLARO` y `DES-56-BITS` con la interfaz en
inglés. Un port literal más i18n está roto de origen.

Aquí el veredicto es un **valor**, la frase es presentación, y **el lote nunca
lee una frase**:

- `ParseResult.summary` es un `Map<SummaryKey, SummaryValue>`;
- `SummaryValue.code()` es un identificador invariante al idioma — es lo que
  cuentan las estadísticas y lo que lleva el CSV;
- `SummaryValue.detail()` es prosa técnica, solo para leer;
- los vocabularios cerrados son enums en `IcsfVocabulary`;
- los avisos del parser también llevan código (`DiagnosticCode`), porque el
  original recuperaba el aviso del byte 59 con `"byte 59" in w`.

`IcsfTextResolver` traduce los códigos al idioma del usuario, en el borde.

---

## 3. Las dos restricciones innegociables

### 3.1 El lote no reinterpreta ni un byte

No es una convención: es la firma.

```java
InventoryMapper.map(BatchEntry entry, ParseResult result)
```

Recibe **el análisis**, no el token. De los bytes solo lee
`entry.data().length`. Todo lo que el original sacaba a mano (`tok[8:16]` para el
MKVP, `tok[48:56]` para el CV, `tok[59]`, `tok[7]>>5`) es ahora un campo tipado
del `ParseResult`. Las 12 dimensiones se copian mediante el enlace declarativo
`InventoryColumn → SummaryKey`.

`FindingDetector` funciona igual: sobre el resumen tipado, los diagnósticos
tipados y los hechos a nivel de byte que el analizador ya registró.

Verificado por `IcsfBatchConsistencyTest`: corpus de 26 tokens × 12 dimensiones ×
3 procedencias, cada celda comparada contra `IcsfTokenParser` reejecutado por
separado. Más un test de que los hallazgos son reproducibles desde el análisis
individual solo (salvo `DUPLICADO`, que es propiedad del lote, no del token).

### 3.2 El aviso de seguridad

El análisis **no descifra nada**: el material de clave protegido solo es
recuperable dentro del coprocesador, bajo su master key. Pero las salidas llevan
los tokens enteros en hexadecimal.

El aviso aparece en: los dos paneles (etiqueta permanente, no descartable), la
portada del informe de lote, el pie del informe individual, el JSON
(`securityNotice`), el `--help` de la CLI, el README y este documento. Los
mensajes de guardado dicen qué contiene el fichero, no solo que se ha escrito.

---

## 4. Lectura de la entrada del lote

Tres formas, detectadas **bloque a bloque** (los bloques se separan con líneas en
blanco; las líneas que empiezan por `#` se ignoran sin cortar el bloque):

1. un token por línea;
2. dos filas del host por token: la superior lleva el dígito hexadecimal **alto**
   de cada byte y la inferior el **bajo**;
3. el bloque entero como un solo token en hex apilado.

Se prueban las tres y gana la que produce tokens que de verdad se analizan. La
puntuación es **2** para un token con familia reconocida y **1** para uno nulo:
cualquier cadena que empiece por X'00' cuela como nula, así que un nulo no puede
empatar con una lectura buena. A igualdad, gana la candidata más temprana (línea
> dos filas > bloque), porque si nada se analiza el informe es más útil contando
el error línea a línea. Las tres se pueden forzar a mano.

**Etiquetas.** `ETIQUETA|hex` se acepta siempre con tabulador, `|` o `;`. Con
coma, dos puntos o espacio hace falta que la parte izquierda **no** sea
hexadecimal, porque esos tres también son separadores válidos *dentro* del hex.
Consecuencia: una etiqueta formada solo por dígitos hexadecimales (`ABCDEF`)
necesita tabulador, `|` o `;`.

---

## 5. Catálogo de hallazgos

Un hallazgo **no es un error del token**: es algo que hay que mirar. Cada código
explica qué es y qué hacer, para que el informe se entienda sin el manual
delante.

Los **códigos siguen en español** a propósito: son identificadores estables, salen
en el CSV y permiten comparar un informe de aquí con uno de la herramienta
original. Lo que se traduce es el título y la explicación.

| Severidad | Códigos |
|---|---|
| Alta | `ENTRADA-NO-RECONOCIDA`, `TVV-INVALIDO`, `DES-56-BITS`, `DES-FUERZA-SIMPLE`, `MATERIAL-EN-CLARO`, `CV-INVALIDO` |
| Media | `DES-FUERZA-DOBLE`, `BYTE59-FUERA-DE-TABLA`, `BYTE59-INCOHERENTE`, `WRAP-ECB`, `NOCV`, `CV-CERO`, `MKVP-AUSENTE`, `HISTORIA-DEBIL`, `LONGITUD-INESPERADA`, `DUPLICADO`, `AVISOS-PARSER` |
| Informativa | `WRAP-MEJORADO`, `ENH-ONLY`, `NO-EXPORTABLE`, `COMP-TAG`, `TVV-AUSENTE`, `PKA-EN-PRUEBAS` |

### Cuidado con `BYTE59-FUERA-DE-TABLA`

Hoy el byte 59 solo admite X'00', X'10' y X'20', y solo tiene sentido en claves
DATA con CV cero. Pero en niveles antiguos de ICSF ese byte estaba
**subdividido** y llevaba también el algoritmo, así que un token creado entonces
puede llevar legítimamente un valor fuera de la tabla de hoy.

Por eso el texto dice *«contrástalo con la documentación vigente cuando se creó
la clave»* y **no** «token corrupto». La diferencia entre las dos lecturas es la
diferencia entre reparar el byte y recalcular el TVV, o montar una ceremonia de
clave nueva. Hay un test (`IcsfModuleI18nTest`) que verifica que esa distinción
sobrevive en los dos idiomas.

---

## 6. Detalles que cuestan de descubrir

- **Exportabilidad DES.** Hay dos controles independientes y ambos deben
  permitirla: el flag byte 6 bit 7 (solo en tokens internos) **y el bit 17 del
  Control Vector**, que es el que aplican de verdad `Key_Export` y
  `Data_Key_Export`. Restrict Key Attribute con NOEXPORT y Prohibit Export ponen
  el bit 17 a 0 sin tocar el flag byte, así que mirar solo el flag byte da falsos
  «exportable».
- **Poner el bit 17 a 0 exige compensar la paridad del byte 2 del CV** (bit 23).
  Si no se hace, toda clave NO-XPORT parece además un CV corrupto y dispara
  `CV-INVALIDO` en falso. Es justo el par de bits que enmascara la búsqueda en la
  Tabla 676.
- **WRAPENH3 ofusca la longitud a propósito**: los bits *key-form* del CV indican
  siempre triple y los offsets 24 y 48 llevan siempre ciphertext. No es un dato a
  interpretar, así que la longitud se reporta como `OBFUSCATED` y no se infiere
  fortaleza efectiva.
- **La comparación de componentes solo prueba algo** con la clave en claro, o
  cifrada con ECB bajo CV cero. Con envoltura mejorada (CBC + confounder) o CV no
  cero, bloques cifrados iguales no demuestran nada; el veredicto se reporta como
  `UNRELIABLE_*`, aparte de los establecidos.
- **TVV ausente ≠ TVV inválido.** p. 1560: un token fijo guardado en un CKDS
  no-KDSR no tiene MKVP ni TVV. Bytes 60-63 a cero significa «sin materializar»,
  no «corrupto».
- **U+00A0.** El separador que descarta `IcsfHex.clean` no es solo el espacio:
  copiar de un emulador de terminal produce espacios duros, y sin eso el token
  falla como si el hexadecimal fuera inválido.

---

## 7. Alcance de la traducción

**Todo el informe está en español e inglés**, incluido el detalle campo a campo.

El mecanismo es que el núcleo **no guarda texto, guarda significado**:
`IcsfText` lleva una clave de bundle y sus argumentos, y las palabras se eligen
al **renderizar**, con un `Locale` que pasa quien llama. No hay estado global
mutable, y cambiar de idioma **no obliga a reanalizar**: el mismo `ParseResult`
se renderiza en cualquiera de los dos.

```java
IcsfTokenReport.renderText(result, origin, token, locale);
IcsfBatchRenderer.renderFull(report, detail, locale);
```

`IcsfText.raw(...)` existe para lo que **no** es traducible: un nombre de clave
decodificado del token, un valor hexadecimal, un patrón como `K1 = K3 != K2`.
Pasarlos tal cual es lo correcto: son datos. Los identificadores técnicos
(`IMPORTER`, `PINVER`, `2048`, `SYM_FIXED_DES_EXT`) también se pasan literales en
los dos idiomas.

Un argumento puede ser a su vez traducible — el tipo principal de un Control
Vector dentro de la descripción de su familia — y `IcsfMessages` los resuelve
recursivamente.

**La CLI se queda en inglés**: no tiene preferencia de idioma, así que usa
`IcsfMessages.DEFAULT_LOCALE`.

### Dos trampas del mecanismo

- **Comillas.** `MessageFormat` solo se aplica cuando hay argumentos, que es la
  misma regla que sigue `I18nService`. Consecuencia: solo los mensajes **con**
  argumentos doblan sus comillas (`X''{0}''`); los demás las llevan sencillas. Hay
  claves que se leen por las dos vías, así que aplicar el formato en una y no en
  otra dejaría comillas dobladas a la vista.
- **Sangría.** `Properties` se come los espacios iniciales de un valor. Las notas
  de procedencia van sangradas a propósito para colgar de su encabezado, así que
  el primer espacio va escapado (`\  compatible con: ...`).

`IcsfDetailI18nTest` guarda las dos: recorre un corpus de 22 tokens × 3
procedencias y exige que **cada** texto resuelva en ambos idiomas, que ninguna
clave de bundle llegue al informe, que no queden comillas dobladas ni
marcadores `{0}` sin sustituir, y que la sangría sobreviva.

---

## 8. Pruebas

| Clase | Cubre |
|---|---|
| `IcsfTokenParserTest` | Las cinco familias, TVV, CV, exportabilidad, componentes, byte 59, procedencia |
| `IcsfBatchReaderTest` | Las tres lecturas, forzarlas, etiquetas frente a separadores hex |
| `IcsfBatchAnalyzerTest` | Cada hallazgo detectado y sin falsos positivos, estadísticas, duplicados |
| `IcsfBatchOutputTest` | Informe de texto, CSV con BOM, JSON, conmutador de detalle |
| `IcsfBatchConsistencyTest` | El invariante lote ↔ individual, token a token |
| `IcsfCoverageTest` | Tokens variable-length y PKA, y que **los 23 hallazgos son alcanzables** |
| `IcsfDetailI18nTest` | Que **todo** el detalle resuelve en español y en inglés |
| `IncludedPaneI18nUITest` | Que un pane incluido traduce su título **y** su contenido |
| `IcsfModuleI18nTest` | Que todo código reportable tiene lectura en ambos idiomas |
| `IcsfTokenControllerUITest`, `IcsfBatchControllerUITest` | Carga real en JavaFX y recorrido de las vistas |

```bash
mvn test -Dtest='Icsf*'
mvn -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw test -Dtest='Icsf*UITest'
```

---

## 9. Limitaciones conocidas

- **PKA está en pruebas.** La decodificación está contrastada con el manual
  (Tablas 637-655) y ejercitada con tokens sintéticos, pero **no validada contra
  un token real de un PKDS**. Sus campos son provisionales y el lote levanta
  `PKA-EN-PRUEBAS` para que quede dicho en el informe. Validar contra un PKDS
  real es el siguiente paso natural.
- **No se porta `icsf_keywrap.py`** (envoltura y desenvoltura de claves). El
  autotest original lo usaba solo para *construir* tokens de prueba; esa tabla de
  CVs por defecto vive ahora en `IcsfTestTokens`, en ámbito de test, porque el
  analizador lee Control Vectors, no los acuña.
- **`icsf-batch` sale con código 3** si hay entradas ilegibles, alineado con el
  comando `batch` que ya existía en esta CLI. El original en Python sale 0.
- **`--json-out`** es la ruta del fichero JSON, no `--json`: en esta CLI `--json`
  ya significa «imprime JSON por stdout». Es la única divergencia de nombres
  respecto al original.
