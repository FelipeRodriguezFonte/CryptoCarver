# Encargo 45: JavaFX en Linux

Base `main` (`70b4bcf`), rama `luna/ci-javafx-green`. Trabajo directo con Sol, sin push.

## Diagnóstico

`testAsymmetricKeyGenerationWorkbenchUI` carga y materializa el shell, genera RSA 2048,
ECDSA secp256r1, DSA 2048 y Ed25519; verifica tarjetas, acciones de reutilización,
copia protegida y borrado. Todo salvo la carga estaba dentro de un único `runAndWait`.
RSA y DSA llaman a `OperationExecutor`: en producción usa `workerExecutor` y devuelve
el resultado a JavaFX. Con `test.mode=true` llama a la tarea de forma síncrona; por tanto
la búsqueda aleatoria de primos (especialmente parámetros DSA) bloquea el hilo FX en
este test. ECDSA secp256r1 y Ed25519 ejecutan su generación breve y la presentación
en FX (sin las búsquedas de primos de RSA/DSA); no explican el timeout medido.
No es necesario cambiar `crypto/` ni la ruta asíncrona RSA/DSA de producción.

`runAndWait` espera un latch 10 segundos. Si se agota, lanza `IllegalStateException`;
no cancela la acción ya en ejecución, que puede seguir cambiando el estado después
de haber fallado el test. Aumentar ese límite escondería el bloqueo.

### Diez ejecuciones aisladas, macOS, software rendering

Comando: `mvn -o -q -Dtest=ModernMainControllerUITest#testAsymmetricKeyGenerationWorkbenchUI -Dprism.order=sw test`.
Instrumentación temporal alrededor de los handlers; solo algoritmo y duración, sin claves.
La duración RSA/DSA incluye el callback síncrono de presentación; no es un benchmark
criptográfico. Cada ejecución usa un JVM nuevo. Todas pasaron.

```text
1: exit=0 test=3.702 wall=17.09 KEY_TIMING RSA 0.332 s, KEY_TIMING DSA 1.465 s
2: exit=0 test=3.717 wall=7.24 KEY_TIMING RSA 0.199 s, KEY_TIMING DSA 1.712 s
3: exit=0 test=5.444 wall=9.09 KEY_TIMING RSA 0.276 s, KEY_TIMING DSA 3.209 s
4: exit=0 test=3.303 wall=6.86 KEY_TIMING RSA 0.366 s, KEY_TIMING DSA 1.076 s
5: exit=0 test=4.932 wall=8.40 KEY_TIMING RSA 0.333 s, KEY_TIMING DSA 2.776 s
6: exit=0 test=4.511 wall=7.98 KEY_TIMING RSA 0.298 s, KEY_TIMING DSA 2.364 s
7: exit=0 test=3.55 wall=6.91 KEY_TIMING RSA 0.275 s, KEY_TIMING DSA 1.430 s
8: exit=0 test=2.055 wall=5.64 KEY_TIMING RSA 0.186 s, KEY_TIMING DSA 0.160 s
9: exit=0 test=7.948 wall=11.39 KEY_TIMING RSA 0.389 s, KEY_TIMING DSA 5.785 s
10: exit=0 test=2.884 wall=6.19 KEY_TIMING RSA 0.264 s, KEY_TIMING DSA 0.858 s

```

### Los siete hallazgos reales en Ubuntu

Se arrancó Docker y se instaló Ubuntu 24.04 AMD64 con Java 17, Maven y las mismas
bibliotecas Xvfb/GTK/Mesa que el workflow, con `--no-install-recommends`. El workflow
no instala una familia de fuentes explícita: se usan las fuentes disponibles de esa
instalación. Docker ejecuta AMD64 emulado sobre macOS ARM, no hardware del runner.
El primer intento ARM no pudo resolver JavaFX 21.0.5 linux-aarch64 y se descartó.

`bash scripts/run-ui-tests.sh -Dtest=ClippedTextRegressionUITest -DclippedTextMetrics=true`
reproduce exactamente siete hallazgos (1 test, 0 fallos, 1 error envolviendo la aserción).
El rótulo de Designer es un **botón dentro del TitledPane**, no su título. Los otros
seis son tres botones de un HBox repetidos en los dos estados, no cabeceras de columnas.
El registro completo con rutas, ids y textos está en `javafx-linux-captions-before.tsv`.

| Estado | id / localización | Texto completo | Ancho / preferido antes | Ancho / preferido después |
|---|---|---|---:|---:|
| Designer | `nodeInspector > Button[5]`, sin id | Guardar configuración del bloque | 238 / 242 | 293 / 242 |
| Workbench | `keyCertificateWorkbench > HBox[1] > Button[0]`, sin id | Load from File... | 111 / 113,1 | 114 / 113,1 |
| Workbench | mismo HBox, Button[1], sin id | Load from Clipboard Shelf | 165 / 167,3 | 168 / 167,3 |
| Workbench | mismo HBox, Button[5], sin id | Detect & Parse | 124 / 125,3 | 126 / 125,3 |
| store headers | Button[0], sin id | Load from File... | 111 / 113,1 | 114 / 113,1 |
| store headers | Button[1], sin id | Load from Clipboard Shelf | 165 / 167,3 | 168 / 167,3 |
| store headers | Button[5], sin id | Detect & Parse | 124 / 125,3 | 126 / 125,3 |

Aunque se selecciona ES, estos tres textos del Workbench permanecen en inglés en la
aplicación actual. No se modifica su traducción en este encargo.

Como contraste, `-DclippedTextFontScale=1.15` en macOS también reproduce los seis
recortes del Workbench: anchos/preferidos 115/122,1, 180/187,2 y 118,5/125,5.
La herramienta escala el tamaño computado de cada Labeled después del CSS y vuelve
a calcular el layout (excepto estilos ligados por el skin). Es una aproximación de
glifos más anchos, no una sustitución del ensayo real de Linux. No cambia AppSettings.

### CSS

Las reglas `.result-panel` y `.metadata-chip` de `components.css` usan
`-fx-background-radius` y `-fx-border-radius` con `-cc-radius-md` y `-cc-radius-sm`.
Los tokens ya dicen `8px` y `4px`; JavaFX resuelve el lookup a Double y el conversor
compuesto de radios espera Size. Las propiedades se ignoran; no basta añadir px al token.
La instantánea original no recogía radios; se amplió para incluir CornerRadii antes
de medir. Línea base comparable (perfil limpio y estados fijados): `/tmp/cc-final45-css-before.txt`.

Además se observaron en informes de la suite avisos String→Paint en `components.css`
para `.button`, `.text-field`, `.text-field:focused`, `.text-area`, `.text-area .content`,
`.combo-box`, `.scroll-pane .viewport`, `.split-pane` y `.split-pane .split-pane-divider`.
Sus valores son lookups de colores (`-color-bg-surface`, `-color-border`,
`-color-border-focus`, `-color-bg-base`, `-cc-on-accent`, `-cc-border-strong`).
No son literales de color inválidos: falta el ámbito que declara los tokens.
En `testNarrowViewportLayout` y otros tests, el shell se adjuntaba a una Scene sin
`styles.css`; las reglas de componentes reutilizadas por la caché CSS no encontraban
los tokens. `UiTestFxml` instala ahora la hoja base al adjuntar el shell, como hace
`CryptoCalculatorModern`. `ModuleHostVisibilityUITest` conserva la carga perezosa de
producción y añade explícitamente esa misma hoja antes de mostrar el Stage. También
lo hacen `ModernMainShellLocalizationCharacterizationTest` y
`ModernMainLooseActionsCharacterizationTest`: las pruebas aisladas de estas dos
clases (12 tests) pasan sin avisos, incluyendo los cambios de idioma.
`CommandPaletteCharacterizationTest` y `HistoryManagementCharacterizationTest`
completan esa preparación en las demás ventanas del shell que usaban el factory
directamente (16 tests aislados, cero avisos). Las declaraciones de tokens y temas se amplían también a
`#rootStackPane` y `.cc-dialog-pane`, para cubrir las raíces de esos subárboles.
La regla Modena `.tree-cell > .tree-disclosure-node > .arrow` usa
`-fx-background-color: -fx-text-background-color`, con el mismo síntoma.
Cuando no se resuelven, JavaFX ignora esas declaraciones. La comprobación final compara estilos computados y avisos durante el ciclo de vida
de la suite. Los avisos de la flecha se producen también al crear/reciclar celdas
antes de que exista un ámbito raíz completo. El override usa los colores Modena
medidos: `#333333` normalmente y `#ffffff` al seleccionar con foco.

## Arreglos y comprobación final

### Generación

El test inicia RSA y DSA con la ruta real de fondo y restaura `test.mode` inmediatamente,
antes de los callbacks, conservando los diagnósticos no modales. Espera un latch ligado
a la visibilidad de la tarjeta de resultado y comprueba después el estado IDLE.
Los bloques `runAndWait` siguen limitados a 10 s. La espera externa tiene un guard de
90 s para diagnosticar una operación que no termina, no para mantener bloqueado FX:
la búsqueda aleatoria de primos tiene variabilidad y Linux emulado es más lento que
las diez mediciones locales (DSA llegó a 5,785 s incluso sin emulación). Se mantiene
el tamaño 2048 y todas las aserciones de salida. Se restaura el perfil de visibilidad,
se retira el listener en finally y se cierra el executor incluso ante fallo.

La primera suite completa en Docker terminó con 375 tests, 0 fallos y un error
adicional en `symmetricWorkspaceKeepsActionsVisibleAndPreservesOtherRoutes`: el primer
CSS/layout del shell materializado excedía 10 s bajo emulación AMD64. Se separan
CSS, layout y las rutas en turnos FX independientes, manteniendo todas las aserciones
y el guard de 10 s. El test aislado pasó tras el ajuste. En la suite completa final de Linux termina
en 11,522 s, distribuidos entre turnos FX; no se amplía el límite de ningún turno.

En la suite completa de Linux, `testAsymmetricKeyGenerationWorkbenchUI` termina
en 5,591 s y las 78 pruebas de su clase pasan (275,819 s de suite).

No se cambió la generación en producción: RSA 4096 ya usa OperationExecutor. La prueba
manual condicional del cambio de hilo no aplica; no se realizó interacción manual.

### Layout

El inspector usa su mínimo calculado y sus botones su ancho preferido natural, también
al volver a mostrar el inspector. El mínimo fijo 250 dejaba solo 238 px útiles.
La fila de entrada del Workbench es ahora un FlowPane: conserva los controles,
espaciado horizontal y tamaños naturales, y crea otra línea cuando no caben.
No se reduce ninguna fuente ni se excluyen rótulos. Las rutas completas y mediciones
finales están en `javafx-linux-captions-after.tsv`. El test real de Linux pasa con
cero hallazgos (16,566 s en la suite completa final); el contraste macOS con
15 % de fuente también pasa.

### CSS: reglas y diff

| Fichero / selector | Propiedad / valor anterior | Corrección |
|---|---|---|
| components.css, `.result-panel` | background-radius y border-radius: `-cc-radius-md` | Override literal `8px` |
| components.css, `.metadata-chip` | background-radius y border-radius: `-cc-radius-sm` | Override literal `4px` |
| components.css:1406, `.button` | background-color: `linear-gradient(to bottom, -cc-on-accent, -cc-border)`; border-color: `-cc-border-strong` | Ámbito de tokens y hoja base del test |
| components.css:1374, `.text-field` | background-color: `-cc-bg-surface`; border-color: `-cc-border` | Mismo ámbito |
| components.css:1383, `.text-field:focused` | border-color: `-cc-accent` | Mismo ámbito |
| components.css:1355/1363, `.text-area` / `.content` | background-color: `-cc-bg-surface`; borde del área: `-cc-border` | Mismo ámbito |
| components.css:1389, `.combo-box` | background-color: `-cc-bg-surface`; border-color: `-cc-border` | Mismo ámbito |
| components.css:1466, `.scroll-pane .viewport` | background-color: `-cc-on-accent` | Mismo ámbito |
| components.css:1471/1475, `.split-pane` / `.split-pane-divider` | background-color: `-cc-on-accent` / `-cc-border-strong` | Mismo ámbito |
| Modena, `.tree-cell > .tree-disclosure-node > .arrow` | background-color: `-fx-text-background-color` | Override `#333333`; selección con foco `#ffffff` |

El log atribuye las reglas importadas a `styles.css`; la tabla identifica su fuente
`components.css` y la última definición aplicable. Hay también definiciones previas
de esos selectores con aliases `-color-*`: surface→`-cc-bg-surface`, border→`-cc-border`,
focus→`-cc-focus-ring` y base→`-cc-bg-app`, con la misma falta de ámbito en escenas
sin tema. No se eliminan esas declaraciones repetidas.

Los overrides permanecen al final de «Legibility overrides». No se reordenan ni
se deduplican reglas. Los tokens de radios siguen disponibles para otros consumidores.

`ComputedStyleSnapshotTool` recoge radios, pinturas de flechas sin leer datos de las
celdas, un diálogo con controles y MetadataChip, y un árbol sintético en estados normal,
seleccionado y seleccionado con foco. Se aplica CSS/layout dos veces para estabilizar
las barras de desplazamiento. La línea base se obtiene restaurando solo CSS y SidePanel de `d74018b`, usando la
misma herramienta y el mismo bootstrap que la versión final. La comparación se hace con un `target/test-home`
limpio en cada JVM, conservando y restaurando el perfil anterior; no se compara un
perfil lleno de datos de la suite con otro vacío. También se fijan los pseudoestados
normales de hover/foco, para que la posición del puntero y la activación de ventanas
no elijan el estado de la instantánea. El árbol sintético mantiene sus tres estados
explícitos. Se incluyen también los nodos ocultos del skin de las barras de
desplazamiento: su primera pasada de layout puede ocultar temporalmente las
flechas, aunque su CSS no cambia. Esto amplía la cobertura; no se filtran nodos
para conseguir el diff ni se cambia la auditoría de rótulos. Las pasadas de
calibración con perfiles/foco o bootstrap distintos se descartaron.
Línea base y final contienen exactamente las mismas **19.288** claves de nodos.

El diff completo está en `javafx-linux-style.diff`: **12 líneas de nodos cambiadas**,
todas limitadas a radios de fondo y borde que antes se ignoraban. Para **cada tema**:

- Hashing, MAC, PIN Generation, Manual Conversion y Key & Certificate Format Workbench:
  el ResultPanel pasa de radios 0 a 8 (cinco líneas por tema).
- El MetadataChip del diálogo pasa de radios 0 a 4 (una línea por tema).

No hay cambios de colores, pinturas de flechas en los tres estados, fuentes, padding,
tamaños ni opacidades. Ignorando únicamente esos radios, el diff está vacío.

### Entorno y validación

macOS 26.6.2 ARM64, Maven 3.9.11, Java 25; Ubuntu 24.04 AMD64 emulado, OpenJDK 17.0.20.1,
Xvfb, software rendering y DejaVu. No se empleó `low-cpu`; se ejecutó un solo Maven cada vez. El build por defecto
usa ahora `surefire.reuseForks=false`, igual que el script Xvfb: libera el toolkit y
su caché nativa/CSS al terminar cada clase, sin excluir tests. La ejecución previa
con toolkit compartido pasó 2.532 tests en 290,51 s, pero conservaba avisos de colores.
Cuatro clases aisladas (26 tests) eliminaron los avisos de colores y dejaron 54 de Modena,
antes de adjuntar árboles a una Scene. La inicialización de `SidePanel` declara el
fallback de `-fx-text-background-color` como Paint literal `#333333` en el propio
TreeView; los overrides de Legibility siguen controlando la pintura normal y con
selección/foco al aplicar el tema. Se completó el arreglo con la hoja base explícita en las ventanas de
caracterización, en lugar de cambiar el factory FXML de producción. La preparación del stylesheet adicional es del helper de tests;
se conserva el bootstrap de producción existente.
Los resultados finales se recogen en las tablas siguientes.


### Suites completas

| Entorno / comando | Tests | Fallos | Errores | Omitidos | ClassCastException | Tiempo total (s) |
|---|---:|---:|---:|---:|---:|---:|
| macOS, `mvn -o -q test` | 2.532 | 0 | 0 | 1 | 0 | 499,34 |
| Ubuntu 24.04, `bash scripts/run-ui-tests.sh` | 375 | 0 | 0 | 0 | 0 | 995,97 |

El tiempo suma arranque, compilación y forks; los XML registran 331,466 s de tiempo
de suites en macOS y 817,205 s en Linux. El omitido es `Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain`,
preexistente y también omitido en la línea base. No se añadió ninguna exclusión ni
condición de sistema operativo.

Salida resumida del script Linux y agregación de sus XML de Surefire:

```text
[ui-tests] running opt-in JavaFX suite with Xvfb
exit=0
Tests: 375; failures: 0; errors: 0; skipped: 0
ClassCastException: 0
Wall time: 995.97 s; suite time: 817.205 s
```

La ejecución de Docker verifica Linux real, Xvfb, las dependencias y fuentes
disponibles de la instalación del workflow, y el script sin exclusiones adicionales.
Usa OpenJDK de Ubuntu; GitHub instala Temurin 17 y ejecuta AMD64 nativo. El run
de GitHub y sus tiempos nativos quedan por confirmar allí: no se ha hecho push
ni lanzado un workflow remoto. No se ha cambiado el workflow.

### Reproducción

```sh
# Cinco ejecuciones separadas, secuenciales:
mvn -o -q -Dtest=ModernMainControllerUITest -Dprism.order=sw test
# Cada una de es/light, es/dark, en/light y en/dark:
mvn -o -q -Dtest=ClippedTextAuditTool -Dprism.order=sw \
  -DclippedTextAuditOut=target/clipped-text-audit-45 \
  -DclippedTextAuditLocale=es -DclippedTextAuditTheme=light test
mvn -o -q test
# Dentro de Ubuntu 24.04 AMD64, con las dependencias del workflow:
bash scripts/run-ui-tests.sh
```

### Cinco pasadas finales de ModernMainControllerUITest, render software

| Pasada | Tests | Fallos / errores / omitidos | Tiempo suite (s) | Tiempo total (s) |
|---|---:|---|---:|---:|
| 1 | 78 | 0 / 0 / 0 | 41.53 | 56.24 |
| 2 | 78 | 0 / 0 / 0 | 50.558 | 64.59 |
| 3 | 78 | 0 / 0 / 0 | 37.823 | 41.55 |
| 4 | 78 | 0 / 0 / 0 | 42.762 | 46.4 |
| 5 | 78 | 0 / 0 / 0 | 46.073 | 49.61 |

Las cinco pasadas finales tienen **0 ClassCastException** en sus logs. Los tiempos
totales incluyen el arranque de Maven y, cuando corresponde, la compilación. Antes de la corrección final
del bootstrap CSS, otras cinco pasadas también pasaron, pero conservaban avisos:
no se usan esas pasadas como evidencia de eliminación de los avisos.

### Auditoría completa de rótulos

| Idioma | Tema | Total de recortes | Tiempo total (s) |
|---|---|---:|---:|
| es | light | 0 | 132,28 |
| es | dark | 0 | 129,74 |
| en | light | 0 | 132,85 |
| en | dark | 0 | 130,72 |

Son recorridos completos de `ClippedTextAuditTool`, además del test de regresión de
los siete rótulos. Los resultados de texto quedan en `target/clipped-text-audit-45`;
no se suben sus imágenes.

### Commits de implementación

- `93be3f0`: diagnóstico y herramientas de medición.
- `d3c7224`: espera asíncrona de generación.
- `d74018b`: tamaños naturales y ajuste de controles en Linux.
- `4a49e2f`: radios, pinturas y bootstrap CSS de tests.

El quinto commit de documentación contiene este informe, las mediciones finales de
rótulos y el diff completo de estilos. Los cuatro commits anteriores separan el
diagnóstico y cada problema. No se han modificado archivos de `crypto/`, no se
han subido imágenes y no se ha hecho push.
