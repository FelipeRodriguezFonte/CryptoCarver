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
