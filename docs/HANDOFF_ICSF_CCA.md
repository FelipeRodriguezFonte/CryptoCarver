# Key tokens ICSF / CCA — arquitectura y decisiones

Análisis de los *key tokens* nativos del coprocesador criptográfico de host de
IBM z/OS (ICSF / CCA), y reproducción en claro de los verbos nativos con los que
el host entrega y recibe claves. Portado desde la herramienta en Python
(`icsf_tokens.py` + `icsf_batch.py` para el análisis, `icsf_keywrap.py` para la
envoltura).

Fuente de contraste: *z/OS ICSF Application Programmer's Guide*
(`csfb400_icsf_apg_hcr77e0`), apéndices B y C. Las referencias de página que
aparecen en el código son de la página **impresa** del manual (la física del PDF
es +54).

---

## 1. Dónde vive

| Capa | Ubicación |
|---|---|
| Núcleo | `com.cryptocarver.crypto.icsf` (sin dependencias de interfaz) |
| Núcleo de envoltura | `com.cryptocarver.crypto.icsf.keywrap` |
| Interfaz | `IcsfTokenController`, `IcsfBatchController` e `IcsfKeyWrapController` en `com.cryptocarver.ui` |
| Vistas | `src/main/resources/fxml/icsf_token.fxml`, `icsf_batch.fxml`, `icsf_keywrap.fxml` |
| Textos | `icsf.*` en los tres bundles; slice `ModuleTextCatalog.icsf()` |
| CLI | `icsf-token`, `icsf-batch`, `icsf-export`, `icsf-import`, `icsf-inspect`, `icsf-resolve` |
| Tests | `src/test/java/com/cryptocarver/crypto/icsf/` y `.../ui/Icsf*` |

**No es un módulo de navegación propio.** Los tres paneles son `fx:include`
autocontenidos dentro de `keys.fxml`, siguiendo el patrón de
`pkcs11_profiles.fxml`: FXML propio, `fx:controller` propio, slice de textos
propio y espacio de nombres i18n propio. `KeysController` solo aporta los
campos `@FXML`, el reenvío del `StatusReporter` y sendas ramas en
`expandSymmetricPane`; **no contiene lógica ICSF**.

Ojo con el orden de esas ramas: `ICSF / CCA Key Export / Import` también contiene
la palabra `ICSF`, así que se comprueba **antes** que la rama genérica, que si no
se lo lleva el analizador de tokens. `IcsfNavigationUITest` lo fija.

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

Hay **dos posturas distintas** dentro del módulo, y conviene no confundirlas.

**Los dos analizadores no descifran nada**: el material de clave protegido solo
es recuperable dentro del coprocesador, bajo su master key. Pero sus salidas
llevan los tokens enteros en hexadecimal.

**La envoltura sí maneja material en claro**, y no es un descuido: reproducir la
aritmética del host exige que se le den la clave y el KEK en claro. Sigue sin
descifrar nada que el coprocesador proteja —no puede—, pero recibe y escribe
claves completas. Por eso su operación se registra con `SecretRisk.HIGH` frente
al `LOW` de los analizadores, y su aviso es propio y distinto: dice que las
claves van en claro y que se usen claves de prueba.

El aviso aparece en: los tres paneles (etiqueta permanente, no descartable), la
portada del informe de lote, el pie del informe individual y el del informe de
envoltura, el JSON (`securityNotice`), el `--help` de la CLI, el README y este
documento. Los mensajes de guardado dicen qué contiene el fichero, no solo que se
ha escrito.

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
Vale igual para la envoltura, bajo el espacio de nombres `icsf.keywrap.*`.

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
| `KeyWrapVectorTest` | Envoltura contra vectores fijos: toda combinación de longitud, tipo, variante y modo |
| `KeyWrapI18nTest` | Que cada nota y cada veredicto de la envoltura resuelve en ambos idiomas |
| `DesEngineSmokeTest` | El motor DES contra el vector clásico de FIPS 46-3 |
| `IcsfKeyWrapCliTest` | Los cuatro comandos: códigos de salida, JSON, ida y vuelta entre invocaciones |
| `IcsfNavigationUITest` | Que los paneles incluidos se abren **y llegan al viewport** al navegar |

```bash
mvn test -Dtest='Icsf*,KeyWrap*,DesEngine*'
mvn -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw test -Dtest='Icsf*UITest'
```

---

## 9. Envoltura: exportar e importar con los verbos nativos

`com.cryptocarver.crypto.icsf.keywrap` reproduce **en claro, byte a byte** lo que
hace el host con `CSNBKEX`/`CSNBDKX` y `CSNBKIM`/`CSNBDKM`. No es TR-31: son los
verbos nativos, y la razón de existir es poder comparar lo que sale del host con
lo que espera el que recibe la clave.

| Clase | Qué resuelve |
|---|---|
| `Des` | DES/TDES, ECB y CBC, sin cribado de claves |
| `DesKeyCheck` | Paridad, KCV ENC-ZERO, verification pattern de `CSNBKYT` |
| `ControlVectorDefaults` | CV por defecto de cada tipo y longitud (Tabla 676) |
| `KeyWrapScheme` | Variante del KEK y modo; envolver y desenvolver |
| `ExternalToken` | Construir y leer el token externo (Tabla 616) |
| `IcsfKeyWrapService` | Las cuatro operaciones |
| `KeyWrapResult` / `KeyWrapReport` | Resultado como significado; palabras al renderizar |

**Por qué DES propio y no un proveedor.** Esto analiza material de host tal como
llega: claves a las que nadie ajustó la paridad, claves que colapsan a DES simple
porque K1 = K2, y las *weak keys* de DES. JCE y Bouncy Castle rechazan varias de
ellas, y ese rechazo convertiría un **hallazgo** en una **excepción**. La
implementación no criba nada a propósito.

**Las tablas no se transcriben.** Las permutaciones de DES y la Tabla 676 se
generaron por introspección del original, no a mano: un dígito mal en cualquiera
de las dos es invisible en revisión y fatal en ejecución.

**Dos esquemas que dan la misma clave son un hallazgo, no dos.** Un CV a ceros
con variante y un KEK NOCV son la misma aritmética (`KEK XOR 0 = KEK`).
`resolver` los deduplica por firma y anota el equivalente al lado; listarlos
aparte sugeriría que la evidencia apunta a dos sitios cuando apunta a uno.

**El byte 4.** La Tabla 616 dice X'01' para clave doble o triple; los hosts
reales dejan X'00'. `ExternalToken.build` acepta las dos, porque la de los hosts
es la que permite comparar byte a byte. La diferencia son exactamente dos bytes:
el 4 y el primero del TVV, que lo suma. El criptograma es idéntico.

**KCV y verification pattern son números distintos.** El KCV de la industria son
3 bytes de cifrar ceros; `CSNBKYT` lo llama ENC-ZERO y da 4; y su verification
pattern por defecto es **otro algoritmo** (p. 1720). Está dicho en una nota
`INFO` de cada informe porque es la mitad de los descuadres cuando dos equipos
comparan «el KCV».

**Riesgo declarado.** La operación se registra con `SecretRisk.HIGH`, frente al
`LOW` de los dos analizadores: aquellos leen bytes, esta recibe la clave y el KEK
en claro porque reproducir la aritmética lo exige, y escribe claves enteras en
sus informes.

**En la CLI.** `icsf-export`, `icsf-import`, `icsf-inspect` e `icsf-resolve`, con
toda la entrada por flags con nombre: entre una clave, un KEK y un token, el
hexadecimal posicional sería ilegible y fácil de transponer. El byte 4 sale por
defecto como lo escriben los hosts reales, y es la Tabla 616 la que hay que pedir
con `--table616-version`, porque comparar contra un token del host es la razón de
ejecutar esto. Con `--json` los veredictos viajan como códigos junto a las
palabras, para poder ramificar sin depender del idioma.

**Pruebas.** `KeyWrapVectorTest` contrasta contra vectores fijos en
`src/test/resources/icsf/keywrap-vectors.json`, que cubren todas las
combinaciones de longitud, tipo de clave, variante y modo; `KeyWrapI18nTest`
recorre cada nota y cada rama resolviendo el texto en los dos idiomas; y
`IcsfKeyWrapCliTest` comprueba lo que un script necesita: códigos de salida,
veredictos como código, y un token que sobrevive el viaje de ida y vuelta entre
**dos invocaciones separadas**.

---

## 10. Limitaciones conocidas

- **PKA está en pruebas.** La decodificación está contrastada con el manual
  (Tablas 637-655) y ejercitada con tokens sintéticos, pero **no validada contra
  un token real de un PKDS**. Sus campos son provisionales y el lote levanta
  `PKA-EN-PRUEBAS` para que quede dicho en el informe. Validar contra un PKDS
  real es el siguiente paso natural.
- **La envoltura no habla con ningún coprocesador**, ni puede. Reproduce la
  aritmética en claro a partir de la clave y el KEK que se le den, así que sirve
  para contrastar y para diagnosticar, no para operar. Un token **interno** está
  cifrado bajo la master key del host, que un coprocesador no entrega: lo que se
  reproduce aquí es el token **externo** que produce Key Export.
- **La envoltura mejorada no se puede reproducir** (WRAP-ENH, WRAPENH2,
  WRAPENH3). Se rechaza explicándolo, en vez de devolver bytes que parecerían una
  clave sin serlo.
- **La envoltura no tiene modo lote.** Los cuatro comandos de CLI operan sobre
  una clave o un token cada uno; para una tanda hay que iterar desde el script
  que los llame.
- **`icsf-batch` sale con código 3** si hay entradas ilegibles, alineado con el
  comando `batch` que ya existía en esta CLI. El original en Python sale 0.
- **`--json-out`** es la ruta del fichero JSON, no `--json`: en esta CLI `--json`
  ya significa «imprime JSON por stdout». Es la única divergencia de nombres
  respecto al original.
