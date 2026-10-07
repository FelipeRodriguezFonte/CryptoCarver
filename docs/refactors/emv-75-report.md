# Encargo 75: EMV, paradas y continuaciones

Estado final tras la tercera continuación: **Secure Messaging, ODA y HCE extraídos; privacidad LUK corregida; HCE pasa sus tres puertas limpias. EMVController: 783 líneas.** El cierre y la tabla de nueve puertas están al final; se conservan todos los registros anteriores.

## Registro inicial y primera parada

**Secure Messaging queda extraído y validado. ODA se retiró al fallar G3 y su reejecución aislada. HCE no se inició.** Se aplicó la regla de detenerse ante una puerta no limpia, conservando la última extracción validada.

## Alcance y estado final

Repositorio base e0027379, rama `codex/emv-3`, worktree `/Users/feliperodriguezfonte/dev/CryptoCarver-emv-3`, creado con el comando solicitado desde e002737. Se trabajó y compiló únicamente en este worktree, con un solo Maven simultáneo. `backup/emv-2-wip` se consultó con git show como referencia; no se fusionó ni se hizo cherry-pick.

EMVController: **988 líneas antes, 945 después**. La extracción ODA llegó a 810 líneas durante su verificación, pero se retiró. El archivo final es byte a byte el de 0808b3c, la fase 5 validada; EmvOdaCoordinator no queda en el árbol. EmvHceCoordinator no se creó.

EmvSecureMessagingCoordinator contiene carga de ejemplos, cifrado del PIN y generación de MAC, con record View, Supplier de controles y Supplier<StatusReporter>. EMVController conserva campos FXML, nombres e ids, getter perezoso y delegados de una línea. La derivación de claves de Secure Messaging permanece en EmvSessionKeyCoordinator; los helpers compartidos que necesita Data Storage/HCE siguen en el controlador. No cambia el comportamiento.

## Mapas y caracterizaciones

[Mapa de Secure Messaging](emv-5-map.md) y [mapa ODA](emv-6-map.md) enumeran métodos, campos, dependencias y el propietario proyectado de cada clave module.emv.*. La tabla de propietarios describe también HCE como destino previsto; el estado final mantiene ODA/HCE en EMVController al detenerse. No se generó mapa 7 ni se comenzó esa fase.

| Fase | Nuevo test UI | SHA-256 de transcripción UTF-8 sin salto final |
| --- | --- | --- |
| 5 | EmvSecureMessagingCharacterizationUITest | `1fddbe0e74ac769692e229cc638067924e109be447fa9648c34987d82bc92d25` |
| 6 | EmvOdaCharacterizationUITest | `4e43fad065dbf13eb60bb6c20e0ee54145a83d9ef29873643aa66f09fce66981` |

Ambos digests se fijaron y verificaron sobre la producción de su fase **sin extraer**, luego pasaron sin modificarse tras la extracción. No se fijan rutas, fechas, tiempos, textos de excepciones JDK/BouncyCastle ni orden de iteración indefinido. Secure Messaging fija salidas deterministas con claves inventadas, ambos esquemas y validación de PIN. ODA fija formas y población de campos, recuperación de issuer/ICC, SDA/DDA/CDA, fallo por static data alterada y clear; excluye módulos RSA aleatorios y caducidad dinámica, con aserciones de verificación independientes.

La fixture usa el shell y controles FXML reales, resultados, historial, Shelf, barra de estado y visor expandido. Bajo MASKED y REDACTED comprueba ausencia de las claves inventadas. ODA conserva una IMK inventada en un campo secreto durante sus operaciones para comprobar también que la receta y las superficies no la filtran; las claves privadas RSA de la tarjeta desechable son transitorias y no se publican en UI. AppSettings y preferencia de idioma se restauran; Shelf se conserva/restaura; el historial se reemplaza por un HistoryManager temporal, se limpia y el shell se cierra. No se usa historial del usuario.

Incidencias previas al digest: [fase 5](emv-5-characterization-failures.md) registra un fallo de compilación del soporte nuevo por FxAction privado, corregido a protected; [fase 6](emv-6-characterization-failures.md) registra ninguna antes de fijar su digest. Las advertencias de logs no se incorporan a las transcripciones.

Antes de las puertas se localizaron y ejecutaron las comprobaciones de claves en fuente: SpecializedFeedbackHeadlessTest conserva dolFormat en EMVController y las claves de derivación/ARQC/ARPC/Track2 en sus coordinadores existentes. EmvOdaPaneTranslationTest comprueba bundles. Pasadas focalizadas tras extraer, exit 0:

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest,EmvSecureMessagingControllerTest,EmvSecureMessagingCharacterizationUITest
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest,EmvOdaControllerTest,EmvOdaCharacterizationUITest,EmvSecureMessagingCharacterizationUITest
```

Estas pasadas incluyen los guards junto a la caracterización y contratos de pane, sin ejecutar la suite completa; no hubo una invocación exclusiva de los guards.

## Las nueve puertas solicitadas

Antes de cada puerta y de la reejecución se borró target/surefire-reports. Se cuentan solo XML TEST-*.xml de esa ejecución. Logs y manifiestos por suite permanecen localmente en target/emv75-phaseN-G*.log y target/emv75-phaseN-gate-results.json. No se mezclan ejecuciones focalizadas.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 5 | G1 | 449 | 2917 | 0 | 0 | 1 | 0 |
| 5 | G2 | 129 | 547 | 0 | 0 | 0 | 0 |
| 5 | G3 | 129 | 547 | 0 | 0 | 0 | 0 |
| 6 | G1 | 450 | 2918 | 0 | 0 | 1 | 0 |
| 6 | G2 | 130 | 548 | 0 | 0 | 0 | 0 |
| 6 | G3 | 130 | 548 | 3 | 0 | 0 | 1 |
| 7 HCE | G1 | — | — | — | — | — | No ejecutada |
| 7 HCE | G2 | — | — | — | — | — | No ejecutada |
| 7 HCE | G3 | — | — | — | — | — | No ejecutada |


Comandos exactos, repetidos en cada fase iniciada:

```sh
mvn -o -q test -Plow-cpu
mvn -o -q test -Plow-cpu -DrunUiTests=true
mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test
```

La referencia e002737 era G1 448/2916/1 y G3 128/546/0 (informes/pruebas/omitidas). Cada caracterización añade un informe y una prueba. Las seis puertas iniciadas corresponden al estado extraído de cada fase; no se declaran puertas nuevas tras retirar ODA. Ver [fase 5](emv-5-gates.md) y [fase 6](emv-6-gates.md).

## Fallo, reejecución y retirada

G3 de ODA falla en los tres métodos de ExpandedViewerLifecycleUITest:

- closingTableViewerDropsItsSnapshotAndScene
- shellShutdownClosesAnOpenResultWindow
- closingTextViewerReleasesItsSceneWhileViewerRemainsAlive

El diagnóstico es `Closed UI fixture is still strongly reachable`: Stage sigue alcanzable al comprobar GC. La clase queda intacta. Reejecución aislada con informes vacíos:

```sh
mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test -Dtest=ExpandedViewerLifecycleUITest
```

**1 informe / 3 pruebas / 3 fallos / 0 errores / 0 omitidas / exit 1**. El fallo se repite; no se ha ejecutado main limpio ni otro JDK para contrastar causas y no se atribuye al JDK ni a main. En las tres puertas de la fase 5 y G1/G2 de fase 6 esta clase pasó.

Se retiró íntegramente la extracción 06dd269 mediante revert --no-commit; retirada y documentación forman el paso d de fase 6. Se conservaron su mapa, caracterización y evidencia anterior a la extracción. No se avanzó a HCE.

## Higiene, restricciones y commits

Contadores exactos del job quality-gates: **0 estilos en línea en FXML / 325 emojis de 325**, sin añadidos. git diff --check pasa. No se tocaron crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. No se añadieron imágenes, .local.md, DMG ni ejecutables. Las transcripciones y scripts auxiliares de target quedan ignorados.

Tests existentes modificados respecto a e002737: **ninguno**. No hubo reasignaciones necesarias, aserciones relajadas ni cambios de umbral. Solo se añadieron dos tests UI y su soporte. ExpandedViewerLifecycleUITest permanece sin cambios.

Los XML de la reejecución confirman Mac OS X / Java 25 (Homebrew). No se ejecutó el CI Linux con Java 17 en esta sesión.

```text
d262fda docs: map EMV secure messaging extraction and key ownership
c0954f6 test: characterize secure messaging workflows and privacy before extraction
0808b3c refactor: extract EMV secure messaging coordinator
cd941cd docs: record clean secure messaging verification gates
73ae494 docs: map ODA coordinator extraction and key ownership
45e5a1b test: characterize ODA verification and privacy before extraction
06dd269 refactor: extract EMV ODA coordinator
```

El commit que contiene este informe (`docs: withdraw ODA extraction after failed gate and report EMV outcome`) cierra el paso d de fase 6 con la retirada y la evidencia. Rama limpia al terminar; sin push ni merge. Encargo detenido conforme a la regla de puertas.

## Continuación: recuperación y nueva parada en G1

Se conserva íntegro el registro anterior. El usuario aporta el contraste del revisor: la clase aislada con opciones G3 falla 3/3 en 06dd269, 0214636 y e002737, y autoriza aceptar **G2 y G3** únicamente si los únicos fallos son los tests de GC y se reproducen igual en la clase aislada sobre la base de la fase. No autoriza aceptar fallos en G1. El dato del revisor se distingue aquí de las nuevas ejecuciones locales.

### Recuperación

Commit nuevo `e9e3944` (`refactor: recover ODA extraction under authorized GC exception`): recuperación **solo en código**, EMVController y EmvOdaCoordinator idénticos byte a byte a 06dd269. No se reescribió historia ni se revertió documentación. Los guards se ejecutaron esta vez en una invocación exclusiva, antes de la puerta, con exit 0:

```sh
mvn -o -q test -Plow-cpu -Dtest=SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest
```

### Puerta y contraste nuevos

| Ejecución | Código | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| G1 | `e9e3944` | 450 | 2918 | 3 | 0 | 1 | 1 |
| G1-gc-extracted | `e9e3944` | 1 | 3 | 3 | 0 | 0 | 1 |
| G1-gc-base | `45e5a1b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G2 ODA | — | — | — | — | — | — | No ejecutada |
| G3 ODA | — | — | — | — | — | — | No ejecutada |

G1 se ejecutó con el comando exacto `mvn -o -q test -Plow-cpu`. Los únicos fallos son los tres métodos GC de ExpandedViewerLifecycleUITest ya enumerados en la parada original. Todos presentan `Closed UI fixture is still strongly reachable`, con Stage alcanzable. La caracterización ODA y la de Secure Messaging pasan (1 prueba cada una, sin fallos/errores/omitidas) dentro de G1.

Reejecución aislada **con opciones de G1**, primero con extracción e9e3944 y luego sobre la base original de fase ODA `45e5a1b`, sin la extracción:

```sh
mvn -o -q test -Plow-cpu -Dtest=ExpandedViewerLifecycleUITest
```

Ambas dan 1 informe / 3 pruebas / 3 fallos / 0 errores / 0 omitidas / exit 1, con los mismos métodos y diagnóstico. El contraste se hizo temporalmente sobre los dos archivos de producción dentro del mismo worktree; se restauraron inmediatamente los archivos de e9e3944 tras medir la base. La base 45e5a1b contiene la caracterización fijada antes de extraer; su controlador es idéntico al de 0214636. XML confirman Mac OS X / Homebrew Java 25. La nueva evidencia respalda que estos fallos de GC también se reproducen sin ODA con opciones G1; no se declara una nueva ejecución de main limpio.

Antes de cada ejecución se borró target/surefire-reports; recuentos exclusivamente de XML nuevos. Logs: target/emv75-oda-cont-G1.log, target/emv75-oda-cont-G1-gc-extracted.log y target/emv75-oda-cont-G1-gc-base.log. Manifiesto con suites, comandos, runtime y fallos: target/emv75-oda-cont-results.json. Las puertas completas nunca seleccionan ni excluyen esa clase; el -Dtest se usó únicamente para el contraste/reintento aislado solicitado. No se modificó ExpandedViewerLifecycleUITest.

### Aplicación de la regla y estado final

La excepción autorizada se limita a G2/G3. Aunque el contraste reproduce 3/3 en la base, **no convierte G1 en una puerta aceptable**. Se detiene el encargo antes de G2, G3 y HCE, y se retira la extracción recuperada mediante revert --no-commit de e9e3944, en un commit nuevo de retirada y documentación. No hay mapa emv-7, caracterización ni coordinador HCE porque esa fase no se inició.

El controlador vuelve byte a byte al de 0214636 / 0808b3c, 945 líneas frente a las 988 originales. EmvSecureMessagingCoordinator sigue extraído y validado por sus tres puertas anteriores; ODA/HCE permanecen en el controlador. Se conservan mapas, tests de caracterización y evidencia de ambos intentos ODA.

Higiene final: 0 estilos en línea FXML / 325 emojis de 325; git diff --check pasa. Ningún test existente modificado, sin cambios en archivos prohibidos ni umbrales. Solo se compiló en CryptoCarver-emv-3, un Maven a la vez. Rama limpia después del commit de cierre, sin push ni merge.

Commits de esta continuación:

- `e9e3944`: recuperación de ODA solo en código.
- Commit que contiene esta sección (`docs: withdraw recovered ODA after G1 GC failure and record continuation`): retirada del código recuperado, recuentos y actualización del informe. No reescribe ninguno de los commits anteriores.

## Segunda continuación: excepción en las tres puertas y fallo previo de HCE

El usuario amplía expresamente la excepción GC a **G1/G2/G3**, manteniendo las dos condiciones: los únicos fallos deben ser exactamente los tres tests de ExpandedViewerLifecycleUITest y la misma clase, aislada con las opciones correspondientes, debe reproducir los mismos fallos sobre el commit base de la fase. Cualquier fallo distinto continúa bloqueando el avance. No se modifica ni se excluye el test GC.

### ODA recuperada y aceptada

`626ee3b` recupera **solo código**, idéntico byte a byte a 06dd269 (EMVController y EmvOdaCoordinator), sin reescribir historia ni borrar documentación. Antes de las puertas pasan en invocación exclusiva los guards:

```sh
mvn -o -q test -Plow-cpu -Dtest=SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest
```

Las tres puertas ODA terminan con exit 1, pero sus únicos fallos son los tres métodos GC ya documentados, con `Closed UI fixture is still strongly reachable`. Se reejecuta la clase aislada con extracción y luego en base 45e5a1b, sin ODA, con las opciones exactas de cada puerta más `-Dtest=ExpandedViewerLifecycleUITest`. Coinciden métodos y diagnóstico; los identificadores de instancia de Stage no se usan como aserción. Los recuentos son:

| Puerta ODA | Reintento con extracción (inf./pr./fallos/errores/omitidas/exit) | Base 45e5a1b, aislada (inf./pr./fallos/errores/omitidas/exit) |
| --- | --- | --- |
| G1 | 1 / 3 / 3 / 0 / 0 / 1 | 1 / 3 / 3 / 0 / 0 / 1 |
| G2 | 1 / 3 / 3 / 0 / 0 / 1 | 1 / 3 / 3 / 0 / 0 / 1 |
| G3 | 1 / 3 / 3 / 0 / 0 / 1 | 1 / 3 / 3 / 0 / 0 / 1 |

Así se satisfacen las dos condiciones de la excepción en cada puerta. **Aceptación autorizada, no éxito limpio de Maven**. La base temporal se mide en el mismo worktree y se restauran inmediatamente los archivos de la extracción antes de la siguiente ejecución. No se compila main. Un solo Maven simultáneo. Los XML confirman Mac OS X / Homebrew Java 25; no se declara una ejecución CI Linux/Java 17. Las caracterizaciones fijadas de ODA y Secure Messaging pasan en las tres puertas, sin cambiar sus digests.

`635a178` registra la aceptación y los nueve recuentos ODA (tres puertas y seis aisladas) en emv-6-gates.md. Logs/manifiesto: target/emv75-oda-cont2-*.log y target/emv75-oda-cont2-results.json. Informes borrados antes de todas las ejecuciones; cada fila cuenta solo XML frescos. Las puertas completas usan los comandos originales sin selección ni exclusión de tests.

### Estado de las nueve puertas, tras esta continuación

La fase 5 conserva sus resultados originales; la fase 6 usa las últimas ejecuciones aceptadas. Los resultados anteriores y las dos paradas se conservan arriba.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Aceptación |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5 | G1 | 449 | 2917 | 0 | 0 | 1 | 0 | Limpia |
| 5 | G2 | 129 | 547 | 0 | 0 | 0 | 0 | Limpia |
| 5 | G3 | 129 | 547 | 0 | 0 | 0 | 0 | Limpia |
| 6 | G1 | 450 | 2918 | 3 | 0 | 1 | 1 | Excepción GC contrastada |
| 6 | G2 | 130 | 548 | 3 | 0 | 0 | 1 | Excepción GC contrastada |
| 6 | G3 | 130 | 548 | 3 | 0 | 0 | 1 | Excepción GC contrastada |
| 7 HCE | G1 | — | — | — | — | — | — | No ejecutada: caracterización roja |
| 7 HCE | G2 | — | — | — | — | — | — | No ejecutada: caracterización roja |
| 7 HCE | G3 | — | — | — | — | — | — | No ejecutada: caracterización roja |

### HCE: mapa y reproducción de privacidad, extracción bloqueada

`f15cd16` añade el mapa completo [emv-7-map.md](emv-7-map.md): cuatro handlers HCE previstos para EmvHceCoordinator, record View, Supplier<StatusReporter>, getter perezoso y delegados de una línea, campos FXML conservados, helpers compartidos que siguen en EMVController y propietario final de cada clave module.emv.*. backup/emv-2-wip sigue siendo referencia de lectura, sin merge ni cherry-pick. No se necesita reasignar tests de fuente.

Sobre HCE **sin extraer**, el nuevo EmvHceCharacterizationUITest comprueba LUK, MSD, qVSDC, clasificación de salida/detalles, validación localizada EN/ES, y privacidad real del shell. Usa UDK inventada fija y el LUK derivado; restaura AppSettings, idioma, Shelf y test.mode, y limpia historial temporal y UI.

Dos ejecuciones focalizadas dan cada una **1 informe / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1**. El fallo de privacidad está en historial bajo MASKED y REDACTED, tras LUK, MSD y qVSDC. El diagnóstico confirma tres entradas por perfil: `EMVController.hceUdkField` está redacted, pero `EMVController.hceMsdLukField` y `EMVController.hceQvsdcLukField` conservan en claro el mismo LUK de 32 caracteres. Resultados, Shelf, estado y visor expandido no muestran ese valor en estas comprobaciones.

La lista de tokens sensibles de UiStateSnapshot reconoce udk y **no reconoce luk**. Es un defecto anterior a la extracción HCE, reproducido sobre el controlador original. Evidencia completa y salida: [emv-7-characterization-failures.md](emv-7-characterization-failures.md); los JSON de diagnóstico en target contienen solo datos inventados de la fixture aislada.

`5c34231` conserva la reproducción roja y su evidencia. **No se fija SHA-256 de una caracterización fallida, no se relajan aserciones y no se comienza la extracción ni sus puertas.** El test HCE queda deliberadamente rojo y la caracterización incompleta; no se declara una rama con suite limpia. La corrección de esa clasificación requeriría modificar comportamiento de privacidad y UiStateSnapshot, expresamente fuera del alcance autorizado. No se tocó ese archivo ni se ocultó el LUK retirándolo de las aserciones. Este fallo es distinto de GC y no queda cubierto por la excepción.

### Entrega actual y commits

**EMVController: 988 → 810 líneas.** Secure Messaging y ODA permanecen extraídos. HCE sigue en el controlador original, con mapa y reproducción del bloqueo. No hay EmvHceCoordinator ni cambio de producción HCE que retirar. No se completaron los pasos c/d de esa fase.

Ningún test existente modificado respecto a e002737; se añaden tres tests UI (SM/ODA fijados; HCE rojo) y un soporte. No se relajan umbrales ni aserciones. No se tocan crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. Higiene del job quality-gates, ejecutada de nuevo con sus comandos exactos: **0 estilos en línea / 325 emojis de 325**, sin añadidos. git diff --check pasa. No se generaron imágenes, .local.md, DMG ni ejecutables.

Commits de la segunda continuación:

- `626ee3b`: recuperación ODA solo en código, idéntica a 06dd269.
- `635a178`: puertas ODA aceptadas con recuentos de contraste.
- `f15cd16`: mapa HCE y propiedad completa de claves.
- `5c34231`: reproducción roja de la filtración LUK anterior a la extracción.
- Commit que contiene esta sección (`docs: record accepted ODA and pre-extraction HCE privacy blocker`): informe de continuación y estado final.

Se conserva toda la historia y ambos registros de parada. Rama limpia tras el commit de informe, sin push ni merge. El encargo queda detenido en la caracterización HCE por un fallo de privacidad fuera de la excepción autorizada.

## Tercera continuación: corrección de privacidad autorizada

El usuario autoriza exclusivamente añadir `luk` a HISTORY_SENSITIVE_TOKENS de UiStateSnapshot, sin ningún otro cambio en ese archivo. Se añade una sola entrada en la misma línea; el resto es idéntico al commit anterior. Esta corrección de privacidad se separa de la extracción HCE.

### Paso 1: alcance y reproducción verde

Inventario de los **1511 ids FXML distintos**, usando los mismos límites camel-case de nameWords, y revisión de declaraciones de controles Java: los únicos controles que incorporan el token nuevo son **hceMsdLukField y hceQvsdcLukField**, ambos TextField de EMVController. Antes no se clasificaban como sensibles; después sí. Ningún otro control cambia. hceUdkField ya era sensible y sigue igual. Inventario local: target/emv75-luk-classification-inventory.json.

La reproducción EmvHceCharacterizationUITest pasa a verde **sin modificar sus aserciones ni su código**. Comando focalizado con UiStateSnapshotTest existente: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=EmvHceCharacterizationUITest,UiStateSnapshotTest`; dos informes seleccionados / 11 pruebas / 0 fallos, errores u omitidas / exit 0. Los tests existentes se mantienen intactos en este paso; ninguno depende de LUK en claro. Log: target/emv75-luk-fix-focused.log.

### Paso 2: test dirigido de recetas LUK

Commit `5836f5e`: UiStateSnapshotTest añade **un método y una fixture separada**, hceLukRecipesRespectVisibilityAndFullLabRestoration y HceRecipeController, con los dos nombres reales de campo. No se añade ningún campo a DummyController ni se cambia ninguno de sus diez tests previos.

En MASKED/REDACTED se comprueba [REDACTED_SECRET] en ambos parámetros y limpieza del contenido al restaurar, con los dos destinos de reintroducción. En FULL_LAB se comprueban conservación exacta y restauración. Usa valor inventado fijo, AppSettings temporal restaurado en finally, y verifica que Shelf permanece intacto. No modifica historial global; solo trabaja con mapas de recetas. La pasada exclusiva UiStateSnapshotTest da 1 informe / 11 pruebas / 0 fallos, errores u omitidas / exit 0. Log: target/emv75-luk-directed.log.

Esta es la **única clase de tests existente en e002737 que se modifica**, por la autorización expresa del paso 2. La modificación es aditiva; no se ajusta ni relaja ninguna aserción anterior. Todos los tests existentes pasan en las puertas finales: ninguno depende de que LUK se guarde en claro. ExpandedViewerLifecycleUITest permanece intacto.

## Tercera continuación: caracterización, extracción y cierre HCE

### Pasos 3 y 4

`0e9a8a9` fija SHA-256 HCE **sobre código sin extraer**, ya corregida la privacidad: `e6708244e8cd007ed2712fc608d1032f752894f01998b444c12ae44865c807ea`. La pasada focalizada vuelve a pasar antes de extraer. El único cambio posterior en el test de reproducción es el digest esperado, habilitando la comprobación de transcripción; las aserciones que detectaron el LUK en historial no cambian. Se conserva el registro rojo y la parada en emv-7-characterization-failures.md.

`5e80b48` extrae EmvHceCoordinator: cuatro acciones, record View con Supplier<TextField>/Supplier<TextArea>, Supplier<StatusReporter>, getter perezoso y cuatro delegados de una línea. Los campos FXML y sus ids permanecen en EMVController. smText/emvHex/emvShow/emvPublish se copian como helpers privados y sus versiones del controlador permanecen para otros consumidores. Se conserva exactamente el multiconjunto de literales de handlers/helpers, comprobado contra 0e9a8a9; las sustituciones operan sobre identificadores fuera de literales. Se conserva comportamiento criptográfico, validación, mensajes, publicación y efectos sobre controles. El cambio de privacidad autorizado es independiente.

La pasada posterior a extraer de EmvHceCharacterizationUITest,EmvVisaHceControllerTest,UiStateSnapshotTest pasa: **3 informes / 15 pruebas / 0 fallos, errores u omitidas / exit 0**. Digest, contratos FXML y test dirigido pasan. Los guards SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest se ejecutan **en invocación exclusiva** antes de las puertas y pasan, sin reasignaciones de claves necesarias ni cambios a esos tests.

### Paso 5: puertas finales HCE

`c64b23e` registra las tres puertas HCE en [emv-7-gates.md](emv-7-gates.md). Las tres pasan limpias; ExpandedViewerLifecycleUITest pasa **3/3 en cada una**, sin reintento ni necesidad de contraste con base. La base prevista era 0e9a8a9 (privacidad corregida y digest fijado, sin extracción). La excepción de GC no se invoca en HCE. Las puertas completas no seleccionan/excluyen ninguna clase; no se modifican anotaciones del test GC.

Antes de cada puerta se borra target/surefire-reports, y se cuentan exclusivamente los XML nuevos. Comandos exactos:

```sh
mvn -o -q test -Plow-cpu
mvn -o -q test -Plow-cpu -DrunUiTests=true
mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test
```

Logs/manifiesto: target/emv75-hce-cont3-*.log y target/emv75-hce-cont3-results.json. Las tres caracterizaciones (SM, ODA y HCE) pasan en cada puerta, sin omitidas, igual que los once tests de UiStateSnapshot. Los XML confirman Mac OS X / Homebrew Java 25. No se ejecutó CI Linux/Java 17 en esta sesión; las nuevas aserciones y digests no dependen de rutas, fechas, tiempos, excepciones de proveedores ni orden de iteración indefinido.

### Nueve puertas efectivas al finalizar

Los registros de paradas y ejecuciones anteriores se mantienen íntegros. SM conserva su validación original; ODA conserva la aceptación autorizada de la segunda continuación, con contraste 3/3 sobre 45e5a1b para cada puerta y recuentos completos arriba; HCE aporta las puertas limpias de esta continuación. ODA y SM también pasan sus caracterizaciones en las puertas HCE finales.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Resultado |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5 | G1 | 449 | 2917 | 0 | 0 | 1 | 0 | Limpia |
| 5 | G2 | 129 | 547 | 0 | 0 | 0 | 0 | Limpia |
| 5 | G3 | 129 | 547 | 0 | 0 | 0 | 0 | Limpia |
| 6 | G1 | 450 | 2918 | 3 | 0 | 1 | 1 | Excepción GC contrastada |
| 6 | G2 | 130 | 548 | 3 | 0 | 0 | 1 | Excepción GC contrastada |
| 6 | G3 | 130 | 548 | 3 | 0 | 0 | 1 | Excepción GC contrastada |
| 7 | G1 | 451 | 2920 | 0 | 0 | 1 | 0 | Limpia |
| 7 | G2 | 131 | 550 | 0 | 0 | 0 | 0 | Limpia |
| 7 | G3 | 131 | 550 | 0 | 0 | 0 | 0 | Limpia |

Respecto a e002737, G1 final pasa de 448/2916/1 a 451/2920/1 y G3 final de 128/546/0 a 131/550/0 (informes/pruebas/omitidas): tres clases nuevas de caracterización y una prueba dirigida añadida a UiStateSnapshotTest.

### Paso 6: entrega y commits de esta continuación

**Encargo 75 completado: EMVController 988 → 783 líneas**, con Secure Messaging, ODA y HCE en sus coordinadores. La derivación SM permanece en EmvSessionKeyCoordinator, como indica el mapa. Maps emv-5/6/7 mantienen la propiedad de las claves runtime, con los consumidores compartidos explícitos y la UI declarativa en ModuleTextCatalog/ModuleI18n. No se cambiaron ids FXML, crypto/, pom.xml, ModernMainController, StatusReporter ni OperationResult. UiStateSnapshot cambia exclusivamente el token luk autorizado; comprobación exacta contra 01d4cb5. No se hicieron ajustes por dependencias del LUK en claro.

Higiene final, con comandos exactos del job quality-gates: **0 estilos en línea FXML / 325 emojis de 325**, sin añadidos. git diff --check pasa. Sin imágenes, .local.md, DMG ni ejecutables añadidos. Todos los Maven se ejecutan secuencialmente en CryptoCarver-emv-3, sin compilar el repositorio principal. Claves inventadas únicamente. La fixture restituye AppSettings/Shelf y limpia historial aislado; el test dirigido no toca historial global.

| Paso | Commit | Contenido |
| --- | --- | --- |
| 1 | `4b8a669` | Token luk y alcance de la corrección de privacidad |
| 2 | `5836f5e` | Test dirigido de recetas y restauración |
| 3 | `0e9a8a9` | Digest HCE fijado antes de extraer |
| 4 | `5e80b48` | EmvHceCoordinator y delegados |
| 5 | `c64b23e` | Tres puertas HCE limpias |
| 6 | Commit que contiene esta sección (`docs: complete EMV report with privacy correction and final gates`) | Informe final conservando todas las paradas y continuaciones |

Rama codex/emv-3 limpia al terminar, sin push ni merge. Las paradas anteriores se conservan como evidencia histórica; sus bloqueos quedan resueltos mediante las autorizaciones explícitas posteriores.
