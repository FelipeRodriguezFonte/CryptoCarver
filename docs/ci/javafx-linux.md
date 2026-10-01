# Encargo 45: JavaFX en Linux

Base `main` (`70b4bcf`), rama `luna/ci-javafx-green`. Trabajo directo con Sol, sin push.

## Diagnóstico

`testAsymmetricKeyGenerationWorkbenchUI` carga y materializa el shell, genera RSA 2048,
ECDSA secp256r1, DSA 2048 y Ed25519; verifica tarjetas, acciones de reutilización,
copia protegida y borrado. Todo salvo la carga estaba dentro de un único `runAndWait`.
RSA y DSA llaman a `OperationExecutor`: en producción usa `workerExecutor` y devuelve
el resultado a JavaFX. Con `test.mode=true` llama a la tarea de forma síncrona; por tanto
la búsqueda aleatoria de primos (especialmente parámetros DSA) bloquea el hilo FX en
este test. No es necesario cambiar `crypto/` ni la ruta asíncrona de producción.

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

| Estado | id / localización | Texto completo | Ancho / preferido antes |
|---|---|---|---:|
| Designer | `nodeInspector > Button[5]`, sin id | Guardar configuración del bloque | 238 / 242 |
| Workbench | `keyCertificateWorkbench > HBox[1] > Button[0]`, sin id | Load from File... | 111 / 113,1 |
| Workbench | mismo HBox, Button[1], sin id | Load from Clipboard Shelf | 165 / 167,3 |
| Workbench | mismo HBox, Button[5], sin id | Detect & Parse | 124 / 125,3 |
| store headers | Button[0], sin id | Load from File... | 111 / 113,1 |
| store headers | Button[1], sin id | Load from Clipboard Shelf | 165 / 167,3 |
| store headers | Button[5], sin id | Detect & Parse | 124 / 125,3 |

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
de medir. Línea base ampliada: `/tmp/cc-style-radii-before.txt`.

Además se observaron en informes de la suite avisos String→Paint en `components.css`
para `.button`, `.text-field`, `.text-field:focused`, `.text-area`, `.text-area .content`,
`.combo-box`, `.scroll-pane .viewport`, `.split-pane` y `.split-pane .split-pane-divider`.
Sus valores son lookups de colores (`-color-bg-surface`, `-color-border`,
`-color-border-focus`, `-color-bg-base`, `-cc-on-accent`, `-cc-border-strong`).
No son literales de color inválidos: falta el ámbito que declara los tokens.
La regla Modena `.tree-cell > .tree-disclosure-node > .arrow` usa
`-fx-background-color: -fx-text-background-color`, con el mismo síntoma.
Cuando no se resuelven, JavaFX ignora esas declaraciones. La comprobación final
comparará estilos computados y avisos durante el ciclo de vida de la suite.

## Arreglos y comprobación final

Pendientes de registrar tras ejecutar los cambios y la validación completa.
