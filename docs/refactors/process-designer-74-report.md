# Encargo 74 — informe final con continuación

## Resultado vigente

Las tres fases están hechas y verificadas en `/Users/feliperodriguezfonte/dev/CryptoCarver-pd-4`, rama `codex/process-designer-4`, base `62588c0b68b33a6b72b3d8f0cd670212277418a8`. Controlador: **1340 → 914 líneas**. No hubo merges, push, cambios de rama, compilación ni Maven en otros árboles. Un único Maven activo en cada momento; las nueve puertas de la continuación pasan.

| Fase | Estado | Líneas antes | Líneas después | Resultado |
|---|---|---:|---:|---|
| 1: ejecución / mapa 7 | Hecha | 1340 | 1030 | ProcessExecutionCoordinator: dry-run, ejecución, cancelación, tabla y traza; propietario de mensajes corregido y probado previamente. |
| 2: paleta / mapa 8 | Hecha | 1030 | 965 | ProcessPaletteCoordinator: construcción, filtrado y doble clic con cantidad de nodos live. |
| 3: eventos / mapa 9 | Hecha | 965 | 914 | ProcessCanvasEventsCoordinator: extracción segura según mapa; mismo punto de instalación, prioridad y consumo. |

Cada coordinador usa record View, getters perezosos y callbacks/suppliers de estado vivo; no almacena una referencia al controlador. Los métodos observados quedan como delegados de una línea. initialize y las 71 declaraciones públicas coinciden con la base. StatusReporter no participa en estos flujos y no existía en el controlador: los mapas justifican no introducir un Supplier sin consumidor ni modificar StatusReporter.

La revisión cerrada de ejecución se delegó a un único agente Luna/low, sin ediciones ni Maven; confirmó equivalencia de preflight, cancelación, callbacks, clasificación y purga. Paleta y eventos se revisaron directamente. No se delegaron más subtareas.

## Caracterización y digests completos

Todos los digests siguientes se verifican con aserciones de la transcripción en las puertas UI normal y con opciones CI. Ningún digest anterior cambió.

| Transcripción | SHA-256 |
|---|---|
| Ejecución completa + dry-run + texto/vacío de paleta | `b925165010bd8ff31c1645f094b3a8073ad12bfa834347cb91212425f987afd3` |
| Secretos suministrados + preflight + FULL_LAB/MASKED/REDACTED | `c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1` |
| Canvas: selección, conexión, arrastre, undo/redo (existente) | `e8d1bafcbc0a35484c18428979328002eb7658976c380b61f40c2a63118ff014` |
| Paleta: texto/categoría/sin coincidencias/espacios/null + colocación live (nuevo) | `e5919c4b74289c4943dec6ca56218164ddff40ffee4baeac5c957bfdaf4a6a76` |
| Eventos: teclado/rueda/click/curva/Escape (nuevo) | `964211ade5ffbf3408904c5eca1160b21bc8227864b80bd2aa83dff50f2c24c3` |

Los fallos previos a fijar los dos digests nuevos están anotados en process-designer-8-characterization-failures.md y process-designer-9-characterization-failures.md: únicamente marcadores deliberados de 64 ceros tras pasar las aserciones de comportamiento. No se detectaron defectos de producto ni se relajaron tests. Los fallos anteriores de ejecución permanecen en process-designer-6-characterization-failures.md.

Antes de cada extracción se verificó la base: ejecución/paleta inicial 3/3; ampliación de paleta 4/4; eventos y tests de lienzo/ventana/ejecución/paleta 13/13, sin fallos, errores ni omisiones. Tras corregir la asignación de propietarios se ejecutó primero `mvn -o -q test -Plow-cpu -Dtest=SpecializedFeedbackHeadlessTest`: 2/2, sin errores ni omisiones, antes de las tres puertas de ejecución.

Portabilidad: se conservan los marcadores de duración y excepción JDK/proveedor. Las nuevas transcripciones no fijan rutas, fechas, IDs, tiempos ni salidas aleatorias. La paleta usa el orden explícito de handlers/catálogo; en eventos se fija la igualdad lógica de sceneToLocal, no coordenadas dependientes de ventanas/OS. Los atajos sintéticos son compatibles con control/meta y los nodos usan posiciones fijas. Las ejecuciones son locales en Mac/JDK 25; las puertas con opciones CI no equivalen a haber ejecutado el runner Linux/Java 17.

## Puertas de la continuación

Se borró target/surefire-reports antes de cada Maven. Los recuentos incluyen únicamente los XML nuevos de esa ejecución.

- G1: `mvn -o -q test -Plow-cpu`
- G2: `mvn -o -q test -Plow-cpu -DrunUiTests=true`
- G3: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| 1: ejecución | G1 | 445 | 2911 | 0 | 0 | 1 | 0 |
| 1: ejecución | G2 | 125 | 541 | 0 | 0 | 0 | 0 |
| 1: ejecución | G3 | 125 | 541 | 0 | 0 | 0 | 0 |
| 2: paleta | G1 | 446 | 2912 | 0 | 0 | 1 | 0 |
| 2: paleta | G2 | 126 | 542 | 0 | 0 | 0 | 0 |
| 2: paleta | G3 | 126 | 542 | 0 | 0 | 0 | 0 |
| 3: eventos | G1 | 447 | 2913 | 0 | 0 | 1 | 0 |
| 3: eventos | G2 | 127 | 543 | 0 | 0 | 0 | 0 |
| 3: eventos | G3 | 127 | 543 | 0 | 0 | 0 | 0 |

No se observó fallo de ExpandedViewerLifecycleUITest; no fue necesario aplicar su excepción preexistente. Las puertas generales tienen una omitida; en fases 2 y 3 se identifica en Pkcs11SessionEncapsulationTest. Las puertas UI no tienen omisiones. La advertencia de traducción Wallet / eIDAS ya estaba en la base y se mantuvo fuera del alcance.

## Privacidad, higiene y alcance

En cada fase pasan ProcessDesignerExecutionCharacterizationUITest y ProcessDesignerSuppliedSecretPreflightUITest. Bajo MASKED y REDACTED, la clave suministrada inventada no aparece en historial, Shelf, barra de estado, visor expandido, filas, telemetría ni traza; el campo PasswordField también se comprueba visualmente enmascarado y undo/redo no contienen la clave. FULL_LAB conserva las superficies permitidas. Las nuevas pruebas restauran AppSettings, Shelf e historial aislado y cierran sus ventanas.

ProcessDesignerTraceRedactionTest, ProcessTelemetryRedactionTest, ProcessDesignerSuppliedSecretPreflightUITest y ProcessDesignerExecutionCharacterizationUITest permanecen intactos. El único test existente ajustado es SpecializedFeedbackHeadlessTest, con justificación por clave en mapa 7: nodeError/aad/iv/ivLabel/failed en ejecución; failed/connectionReversed en controlador. Se mantienen presencia en fuente y paridad EN/ES; no se eliminó ninguna aserción ni se relajó un umbral.

Los comandos exactos del job quality-gates de `.github/workflows/ui-tests.yml` pasan sobre el código final: **0 estilos FXML en línea**, **325 emojis / máximo 325**. Sin imágenes, .local.md, secretos reales ni nuevas claves de idioma.

Confirmado por diff respecto a la base: **ningún fichero de crypto/ ni pom.xml modificado**. Tampoco ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. Los únicos cambios de producción son ProcessDesignerController y los tres coordinadores nuevos. No cambió ninguna cadena literal por un renombrado regex.

## Registro del intento inicial, retirado

El intento anterior quedó parcial: mapa y caracterización verificada; extracción ensayada de 1340 a 1030 líneas y retirada después de la primera puerta. Esa ejecución produjo 445 informes / 2911 pruebas / 1 fallo / 0 errores / 1 omitida, exit 1. G2/G3 y fases siguientes no se iniciaron por la regla de parada; el controlador volvió a 1340 líneas y la rama quedó limpia con documentación.

El único fallo fue mi asignación equivocada de module.process.feedback.connectionReversed a ProcessConnectionCoordinator en SpecializedFeedbackHeadlessTest. La clave permanecía en ProcessDesignerController. Fue un error de este encargo, no un fallo de comportamiento ni preexistente. La continuación autorizada corrige esa asignación y conserva la evidencia histórica en el mapa 7. No se atribuye el fallo a JDK 25 ni a main.

## Commits y entrega

Commits desde la base, en orden:

1. `ebd204a` — docs: map process execution extraction phase 7.
2. `14dd46f` — test: record verified execution characterization baseline.
3. `10b947c` — docs: record phase 7 gate failure and stop assignment 74.
4. `23a7540` — refactor: extract process execution coordinator with verified feedback owners.
5. `61fc497` — docs: map process palette extraction phase 8.
6. `826227b` — test: characterize process palette category filters and live placement.
7. `7c9647e` — refactor: extract verified process palette coordinator.
8. `f0b026a` — docs: map safe canvas event extraction phase 9.
9. `419908d` — test: characterize installed canvas keyboard and pointer events.
10. `0414499` — refactor: extract verified process canvas events coordinator.
11. Commit que contiene este informe — docs: finalize assignment 74 continuation report.

La rama queda limpia después del commit del informe. No quedan cambios sin verificar ni bloqueos. Logs y recuentos locales permanecen en target, ignorado por Git; la evidencia durable está en los mapas, las notas y este informe. Cada fase tiene commits separados de mapa, caracterización y extracción para poder revertirla independientemente.
