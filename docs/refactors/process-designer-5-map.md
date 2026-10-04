# Process Designer: mapa de fase 5

Base para esta fase: rama `codex/process-designer-2`, fase 4 cerrada en `c1caa28`. `ProcessDesignerController.java` parte de 1333 líneas.

## Alcance real en la base

En esta rama, `showPreflightFailure(String)` ocupa 7 líneas (1133–1139), no 148. El chequeo que la llama está en la primera parte de `handleRunProcess()` (1033–1061): lee la definición ejecutable y detecta algoritmo simétrico inválido, conexión AAD en un algoritmo sin AAD y conexión IV en un algoritmo que no usa IV. Esa orquestación permanece en el controlador; esta fase extrae la presentación común del fallo.

## Estado vivo leído y escrito

| Estado | Lectura | Escritura | Observación |
|---|---|---|---|
| `executionStatusTable` | Comprueba si existe | Reemplaza filas por una fila `validation / PRE-FLIGHT / ERROR / 0 ms` | Solo cableado de UI. No altera estado del proceso. |
| `executionOutputArea` | No lee contenido previo | Muestra `module.process.feedback.failed` con el mensaje preflight | `handleRunProcess` limpia el área antes de validar. El presenter conserva la misma envoltura y orden. |
| Mensaje detallado | Lo recibe ya traducido | No cambia el modelo | Se forma en el controlador a partir del nodo, algoritmo y error; no incluye los secretos transitorios. |
| Progreso, estado, botones | No los consulta | No los cambia | Solo se ajustan después de que todos los chequeos pasen. Mantenerlo así. |
| Definición, conexiones y secretos transitorios | No los consulta | No los cambia | La validación temprana evita iniciar ejecución; no escribe historial ni telemetría. |

## Lógica frente a cableado de UI

- La decisión de qué preflight falló y el mensaje específico pertenecen a `handleRunProcess`; este método controla el primer error y termina el handler.
- Crear la fila de validación y renderizar el error general en `executionOutputArea` son presentación separable. `ProcessPreflightPresenter` recibirá un `View` fresco con suppliers de la tabla y el área, y una función de traducción.
- El presenter no debe retener el `View`, la pantalla ni el controlador. El wrapper `showPreflightFailure` queda como delegado. El `ProcessDesignerController` no cambia su API pública.
- El test nuevo utilizará solo un secreto sintético de `ENCRYPT` bajo perfiles `MASKED` y `REDACTED`; comprobará que no aparezca en inspector visible, salida, estado, telemetría ni historial. También restaurará `AppSettings`, Shelf e historial.

## Cobertura de partida y huecos

`ProcessDesignerSuppliedSecretPreflightUITest` cubre la provisión y retractación de secretos en nodos, pero no verifica el presenter. `ProcessDesignerControllerTest` cubre opciones AAD y conexión a puertos, no fija la fila ni el texto de los tres fallos preflight. `ProcessDesignerUX12UITest` y `ProcessDesignerWindowUITest` aportan cobertura del flujo de ejecución, pero tampoco una transcripción con digest.

La nueva `ProcessDesignerPreflightCharacterizationUITest` fijará el comportamiento de algoritmo desconocido, AAD incompatible e IV no utilizado, además del estado de ejecución que debe permanecer intacto y la ausencia del secreto sintético en salidas. Se anotarán fallos de producto antes de corregirlos y la transcripción se fijará tras pasar sus aserciones.

## Decisión de separabilidad

La presentación común de siete líneas es separable: solo recibe un mensaje y modifica dos controles UI. El controlador retiene los chequeos, el orden y el retorno temprano. La diferencia entre el tamaño indicado en el encargo (148 líneas) y el archivo de esta rama queda registrada; no se ampliará la extracción a la ejecución asíncrona ni al resto de `handleRunProcess`.

## Cierre de fase 5

Extracción realizada en `a297c9d`: `ProcessPreflightPresenter` crea la misma fila y traduce la misma envoltura de error. El controlador construye el `View` con suppliers en cada llamada y delega el método privado. La API pública no cambia. Como el método de esta rama solo tenía siete líneas, la configuración del getter y el `View` aumentan el controlador de 1333 a 1340 líneas; el controlador queda 463 líneas más corto que al inicio del encargo.

`ProcessDesignerPreflightCharacterizationUITest` mantiene el digest SHA-256 `1234b586aacd1d973763894b18878d474d46a5849630a6a8e3b061ff0137cf74` (64 caracteres); se comprobó con los tres fallos y ambos perfiles.

Puertas tras la extracción, ejecutadas por separado:

- `mvn -o -q test -Plow-cpu`: exit 0; 2861 tests, 0 fallos, 0 errores, 1 omitido.
- `mvn -o -q test -Plow-cpu -DrunUiTests=true`: exit 0; 2861 tests, 0 fallos, 0 errores, 1 omitido. `ExpandedViewerLifecycleUITest`: 3 tests, 0 fallos, 0 errores, 0 omitidos; no se necesitó cotejo con `main`.

El fallo inicial del fixture y el digest provisional están anotados en `process-designer-5-characterization-failures.md` antes de modificar el fixture y fijar el SHA. No se detectaron defectos de producto en esta fase.
