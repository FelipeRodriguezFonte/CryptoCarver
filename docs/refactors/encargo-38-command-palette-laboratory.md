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

`CommandPaletteCharacterizationTest` se ejecutó antes de cambiar producción con `mvn -o -q -Dmaven.repo.local=/Users/feliperodriguezfonte/.m2/repository -Duser.home=target/test-home -Dtest=CommandPaletteCharacterizationTest test`. Salida: `Tests run: 5, Failures: 0, Errors: 1, Skipped: 0`. El único error fue intencional: al abrir y cerrar la paleta, el foco quedó en `commandSearchField` en vez de volver al anterior (`TitledPane` de Hashing). Búsqueda/ranking, resultados vacíos, Enter/Escape y localización del menú pasaron. El test dirigido dejó después 8 pruebas UI y 2 unitarias verdes (0 fallos/errores), incluyendo restauración de foco, navegación del menú, orden de favoritos y textos en ambos idiomas.

La prueba `user.home=target/test-home` usó el FXML de producción mediante `Fxml.loader` y `test.mode`; los ajustes de idioma, visibilidad, favoritos y `lastRoute` se restauran. El perfil `REDACTED` solo regula la presentación de secretos y no oculta rutas de módulos por sí mismo. Por eso se valida que cada comando de navegación tenga ruta resoluble y que el catálogo no contenga valores de entrada del usuario; no se inventó una política de ocultación de módulos que no existe en `AppSettings`.

## Instantáneas visuales

Se capturó `Hashing`, tema `theme-dark.css`, con `user.home=target/test-home`: `target/encargo38-before.txt`, luego `target/encargo38-after-palette.txt` y `target/encargo38-after-menu.txt`. Los dos `diff -q` desde la base devolvieron vacío. La herramienta actual no expone una opción para abrir la paleta antes de capturar, así que el overlay no pudo incluirse. No se modificaron CSS ni FXML.

## Resultado y límites

Commits de esta rama, en orden:

1. `d3db208` — inventario.
2. `8c86fbe` — caracterización previa.
3. `bc568fd` — extracción de la paleta y catálogo.
4. `8979479` — extracción del menú Laboratorio.
5. Commit de documentación (este informe).

La suite completa `mvn -o -q test` terminó primero con 2423 pruebas, 0 fallos, 0 errores y 1 omitida, en 236,44 s. Tras los últimos retoques de las aserciones de caracterización, la ejecución completa posterior produjo literalmente `OutOfMemoryError: Java heap space` durante el despacho de JavaFX; Surefire guardó el volcado en `target/surefire-reports/2026-09-29T15-40-50_037-jvmRun1.dump`. Los XML de esa ejecución enumeran 2423 pruebas sin fallos/errores, pero la excepción del proceso impide certificarla como una ejecución limpia. Se detuvieron las comprobaciones Maven tras el OOM y no se repitió la suite.

`ModernMainController` tenía 3146 líneas en el commit base `1a52509` y queda en 3002. No se modificaron `crypto/`, FXML, CSS, `pom.xml` ni `.mvn/`. No se hizo prueba manual. Las pruebas nuevas no incluyen ni registran secretos o valores de entrada de usuario; el informe omite la salida de consola de pruebas heredadas.
