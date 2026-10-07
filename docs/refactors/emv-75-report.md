# Encargo 75: extracción EMV y parada en ODA

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
