# Encargo 54: arranque y router

Base: main `4e9f496`, con el 53 fusionado. Rama `codex/modern-main-initialize`. ModernMainController: 2474 líneas antes del encargo.

## Orden y dependencias de initialize()

93 líneas originales. Orden contractual: dialogs → deferred modules → router → navigation chrome → Shelf reporter → progress handlers → lifecycle → laboratory → palette → accelerators → inline errors → security tip listener → status presenter → visibility selection → responsive layout → rail/side panel/navigation controller → i18n refresh/listener/painting → history → session trail → symmetric keys load → symmetric keys show → saved route → fonts → result support → deferred table support.

Los hosts se configuran antes de resolver módulos. Router y chrome preceden a las acciones de navegación. Progress handlers preceden a cualquier operación. Menú Laboratory precede a localización. Los presentadores de error/estado, rail y side panel deben existir antes de aplicar localización (que también puede crear session trail al refrescar navegación). La navegación debe estar instalada antes de cargar/mostrar Keys y restaurar AppSettings.lastRoute. Las fuentes se aplican al árbol ya cargado; los soportes de resultados se instalan síncronamente después, y tablas se encola con Platform.runLater, sin adelantarlo.

Construcción: dialogs, chrome, inline errors, status presenter y navigation controller a partir de referencias FXML y callbacks. Cableado vivo: asignación de servicios al shell, handlers del executor, registro de listeners, selección de privacidad actual, navegación, lectura de AppSettings y materialización de hosts. History y session trail son perezosos y pueden existir antes de su fase explícita: la transcripción debe registrar la presencia real, no asumir su momento de creación.

## createNavigationRouter()

48 líneas originales. Construye un EnumMap de callbacks y NavigationRouter. Las claves/rutas y el dispatch por tipo/variant son construcción fija. Host lists, placeholders, resolución de controller, operación activa, controllers Keys/Process Designer y callbacks de carga/expansión son vivos: deben resolverse en cada activación, sin capturar una instancia de módulo. NavigationRouter resuelve controller antes de beforeActivation/hide/host/callback; ese orden no cambia.

## connectShellServices()

El cuerpo despacha por tipo JOSE/COSE/Wallet/History/Shelf/Generic. Conecta StatusReporter, OperationNavigator, historyManager y los format combos; Generic conecta también workbench/envelope si existen. Debe seguir invocándose desde ensureModule, después de showConfigured y antes de devolver el controller: dejar ese punto de invocación en el shell. Un pase inicial único sería incorrecto por la carga perezosa. La selección por tipo es extraíble con suppliers de servicios; no se cachean controllers.

## Caracterización y límites

Se añadirá un observador de fases sin efecto por defecto, sustituible por una subclase en la fixture FXML. Cada fase se observa después de su acción real; se transcriben servicios presentes, hosts configurados/cargados y ruta. Así se fija orden y presencia, no solo una lista final de nombres. Se mantiene también el límite asíncrono de tablas. La fixture conserva AppSettings íntegro y Shelf sin vaciarlo.

Se comprobarán listeners de locale, lifecycle, security tip, responsive width y callbacks de navegación/executor. Los listeners internos de componentes y módulos pertenecen a sus propios ciclos de vida. El ciclo de Scene/Window y los loaders complejos se mantienen en el shell por su relación con campos y llamadas de navegación. Su posición de arranque se conserva mediante callbacks estrechos.

## Fallos antes de fijar el digest

Primer intento de fixture: ruta JOSE inexistente y tipo de contenedor erróneo; se corrigieron usando JWT (Signed) y VBox antes del pase válido. Pase previo válido: 5 tests, 5 fallos, 0 errores. Uno es la comparación deliberada BASELINE_PENDING. Cuatro regresiones independientes confirman que shutdown deja registrados locale, callbacks de rail/side panel, security tip y responsive width. Lifecycle y progress handlers sí se retiran. Transcripción aún sin fijar: SHA `ef5149306b2b89b387c29338d83a522c675b3ff138f8fac9d36c72dd237ce848`, con shutdown.locale.registered=true. No se aceptará esa fila como comportamiento esperado; se corregirá el cierre antes de fijar el digest.

## Arreglo de cierre antes de fijar caracterización

Un único arreglo de propiedad de listeners de arranque: se guardan las referencias registradas y shutdown retira locale/security tip/width, además de los callbacks de navegación. Se conserva el cierre idempotente y se evita ejecutar los trabajos encolados de responsive/table support tras cerrar. Las cuatro regresiones que fallaban pasan; el pase queda en 5 tests/1 fallo, solo BASELINE_PENDING. La transcripción cambia exclusivamente shutdown.locale.registered=true → false. SHA corregido y revisado antes de fijarlo: `116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba` (UTF-8 con salto final). La reparación conserva las 25 fases y sus snapshots: session trail aparece al localizar, antes de history; Keys carga dos hosts y la ruta Hashing carga el tercero.

## Extracción y límites conservados

Caracterización corregida antes de extraer: 5 tests, 0 fallos/errores/omisiones. Tras extraer, pase dirigido con -Plow-cpu: ShellStartupCharacterizationUITest (5), ModernMainShellLocalizationCharacterizationTest (10), LocalizationShellCharacterizationUITest (3), ShellWindowLifecycleUITest (1): 19 tests, sin fallos, errores ni omisiones. Se reforzaron las cuatro regresiones para comprobar registros activos antes del cierre, no solo ausencia después. El SHA de arranque permanece `116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba`; el del 53 permanece `a57ad3eaf8e958a7768a7ecee4bbd22f16b43fee7d4e0985a1b5895df82fdbfb`.

ShellStartupCoordinator contiene record View y vistas de chrome/presentadores, asignadores de servicios, acciones de integración y suppliers vivos. Ejecuta exactamente las 25 fases observadas. La comparación mecánica de sus llamadas step con los checkpoints de initialize antes de extraer es idéntica. createNavigationRouter es factory estático: construye el map fijo y resuelve hosts, controllers, Keys/Process Designer y operación activa al navegar. connectShellServices se invoca desde el punto original de ensureModule y recibe servicios por suppliers.

initialize, createNavigationRouter y connectShellServices son delegados de una línea. Se conservan en ModernMainController el lifecycle de Scene/Window, responsive width/guard, loaders/materialización y resolución/asignación de controllers; el coordinador los invoca en su posición original. El puente shelf.setNavigator permanece como callback estrecho del shell porque esa API exige ModernMainController para inyección de datos. No se amplía su API ni se captura una instancia de módulo en el coordinador: el callback recibe el Shelf actual como argumento. Los getters de controller del router son perezosos, nunca una instancia guardada al arrancar.

El coordinador registra y retira locale, security tip y callbacks de navegación; responsive width se retira en el shell y lifecycle/executor conservan sus cierres originales. Los trabajos encolados de responsive/tablas siguen diferidos y se ignoran tras shutdown. No se reordenó ninguna fase ni se adelantó un módulo. El observador de fases es un método sin efecto por defecto; la fixture lo sobrescribe para observar estado real sin logs de producto ni reflexión en producción.

## Informe final

JDK Temurin 21.0.8. Ejecuciones independientes con informes limpios entre pases:

| Suite | Comando | Tests | Clases | Fallos | Errores | Omitidos | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| UI | `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 467 | 92 | 0 | 0 | 0 | 0 |
| Completa | `mvn -o -q test -Plow-cpu` | 2810 | 406 | 0 | 0 | 1 | 0 |

Única omisión preexistente: `Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain`. Las cinco pruebas de arranque pasan en ambas suites. Informes locales independientes: target/startup-ui-reports y target/surefire-reports.

ModernMainController: **2474 → 2362 líneas (−112)** respecto de main. Tras añadir el observador y el arreglo de cierre, la base previa a extracción era 2515 líneas: la extracción reduce 153. ShellStartupCoordinator nuevo: **221 líneas**. initialize() original (93 líneas), createNavigationRouter() (48) y connectShellServices() quedan como delegados de una línea.

Digests SHA-256 comprobados después de cada suite:

- Transcripción de arranque, 35 filas UTF-8 con salto final: `116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba`. Idéntico al fijado después del arreglo y antes de extraer. Archivo generado target/shell-startup-transcript.txt.
- Transcripción del 53: `a57ad3eaf8e958a7768a7ecee4bbd22f16b43fee7d4e0985a1b5895df82fdbfb`, sin cambio.
- ModernMainController.java: `51a04842ada0443258dd04f924e7a138fb8f2f7ce08e02406f91f266f14736fe`.
- ShellStartupCoordinator.java: `574ada6b2f930630aa2989575fa25f91396f7b1b5a73f4be649d9d38f92d9777`.

No cambió el código después de los gates. Diff completo contra 4e9f496 sin errores de whitespace. Solo se modificaron ModernMainController, el nuevo coordinador, la nueva fixture y este mapa. No se tocó crypto, no se añadieron imágenes, .local.md ni secretos reales. Cinco commits separados: mapa, caracterización de fallos, arreglo de cierre, extracción e informe. Rama `codex/modern-main-initialize` sin cambios pendientes.
