# Encargo 38: paleta de comandos y menú Laboratorio

## Inventario previo

### Paleta de comandos

`initialize()` llama a `initializeCommandPalette()` durante la carga del FXML. Este registra `Shortcut+K` en `rootStackPane.sceneProperty()` como acelerador de escena y configura la celda, filtro y teclado. Además, `main-view-modern.fxml` conecta `commandPaletteMenuItem` y `toolbarSearchButton` directamente con `handleOpenCommandPalette`; el primero declara también `Shortcut+K` en FXML. El catálogo de atajos lo registra `KeyboardShortcutRegistry` con `Command Palette` / `Shortcut+K`, y el acelerador del menú se sincroniza en `syncMenuBarAccelerators()`.

`handleOpenCommandPalette()` es el contrato público usado por FXML y pruebas; reconstruye el catálogo y muestra el overlay. `handleCloseCommandPalette()` es el contrato público y callback de Escape. `handleExecuteSelectedCommand()` está enlazado desde Enter y doble clic y ejecuta el `CommandItem` seleccionado. `filterCommandPalette()` se invoca por cambio del campo de búsqueda y al abrir.

Campos FXML: `rootStackPane`, `commandPaletteOverlay`, `commandSearchField`, `commandResultsListView`, `commandEmptyLabel`. Estado de vista no FXML: `allPaletteCommands` y `filteredPaletteCommands`. Estado externo consultado por la paleta: `UiNavigationRegistry`/`OperationRegistry`, `AppSettings` mediante proveedores/handlers de operaciones y favoritos, y `I18nService`; acciones ejecutables navegan o llaman handlers del controlador principal. Los handlers y disponibilidad de módulo se consultan al crear cada catálogo, no se deben retener controladores materializados.

Separación: catálogo, visibilidad/rutas, contenido/localización, favoritos, disponibilidad y ranking pertenecen al modelo puro; overlay, celdas JavaFX, foco anterior, flechas/Enter/Escape, eventos de teclado y ratón pertenecen a `CommandPaletteCoordinator`.

Atajos de teclado relacionados: `KeyboardShortcutRegistry` declara `Shortcut+K`; `main-view-modern.fxml` declara el acelerador en `commandPaletteMenuItem`; `initializeCommandPalette()` añade un acelerador `KeyCodeCombination(K, SHORTCUT_DOWN)` a cada escena observada por `rootStackPane`; `syncMenuBarAccelerators()` aplica los atajos del registro a elementos de menú por nombre. La barra de herramientas abre la paleta por `onAction`, sin atajo propio.

Pruebas/callers encontrados con `rg`: `ModernMainControllerUITest` abre, ejecuta y enumera comandos; `KeyboardShortcutsAndMockupCleanupTest` comprueba trigger y registro; otras pruebas UI cargan el mismo FXML/controller, sin invocar los privados directamente. No se encontraron referencias FXML a los privados `initializeCommandPalette`/`filterCommandPalette`.

### Menú Laboratorio

`initialize()` llama a `setupLaboratoryMenu()`. El método examina `mainMenuBar`, evita duplicar un menú con `userData=laboratory`, crea Quick Start y submenús para `PaymentProfileManager.getAllProfiles()`. Cada perfil añade `Load Data` (navega y pide al controlador de módulo que cargue el perfil) y `Run and Verify` (ejecuta el verificador y muestra un Alert excepto bajo test/headless). `showQuickStart()` y `handleItemSelected()`/`navigateToModule()` son los callbacks del shell.

Campo FXML relacionado: `mainMenuBar`; `laboratoryMenu` es el menú localizado que ya viene declarado en FXML. El menú creado dinámicamente se añade una sola vez, identificado por `userData`. Estado compartido: lista de perfiles de pago y proveedores de controladores `keysController`, `emvController`, `paymentsController`; el coordinador debe consultarlos bajo demanda. No se capturan instancias de módulo al construir el coordinador.

Separación: `LaboratoryMenuCoordinator` posee creación/presentación de items, pero recibe callbacks y proveedores para navegación, carga/aviso; el shell conserva inicialización como delegado.

## Caracterización previa

Pendiente de ejecutar y completar antes de extraer la implementación.

## Instantáneas visuales

Pendientes antes/después de la extracción. No se modifica CSS ni FXML.

## Resultado

Pendiente.
