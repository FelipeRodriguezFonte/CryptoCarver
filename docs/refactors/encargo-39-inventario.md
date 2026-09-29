# Encargo 39: inventario previo

## Rama y punto de partida

- Rama: `luna/split-modern-main-12`, creada desde `bc2bb0b`.
- `ModernMainController` parte de 3002 líneas.
- El inventario se hizo con `rg` sobre controladores, navegación, catálogo de comandos, FXML y tests.

## Parte 0: Escape de la paleta

La caracterización anterior llamaba `fireEvent` directamente sobre el `TextField`, y comprobaba visibilidad, pero no probaba la apertura por el acelerador de escena ni que el dueño del foco fuera el campo.

La nueva caracterización carga `/fxml/main-view-modern.fxml`, ejecuta la acción registrada para `Shortcut+K`, comprueba el foco, y envía `KEY_PRESSED` mediante `Event.fireEvent(scene.getFocusOwner(), ...)`.

También cubre la apertura desde el botón de barra y Escape con foco en la lista.

Resultado: verde; no se requiere cambio de producción. Esto descarta el defecto en la ruta de eventos JavaFX simulada por la prueba, pero la comprobación manual solicitada no se ha hecho.

## `handleSaveSession`

### Entradas y llamadores

- `main-view-modern.fxml`: `saveSessionMenuItem` y `toolbarSaveSessionButton` llaman a `handleSaveSession`.
- El inventario de navegación registra `Saved Sessions` como ruta de `UiNavigationRegistry.Module.SAVED_SESSIONS`; esa ruta muestra el gestor, no invoca el diálogo de guardado.
- `handleSaveSession` es público, pero no está incluido como acción nombrada del catálogo de la paleta ni de `OperationNavigator`.
- El catálogo de shortcuts registra `Save Session` como `Shortcut+S`; el menú FXML contiene ese acelerador.

### Estado y datos usados

- No lee campos `@FXML` de entrada; construye y presenta sus propios diálogos JavaFX.
- Lee `AppSettings.getSecretVisibilityProfile()`, `sessionTrailState.log()` y el mapa devuelto por `captureUIState()`.
- `captureUIState()` incluye el estado activo; `UiStateSnapshot.holdsSecretValue` clasifica valores sensibles.
- Persiste mediante `savedSessionsCoordinator().save(name, password)`, que usa `SavedSessionsManager` y proveedores para captura/restauración, selección actual, subtítulo y nodo propietario.
- Emite una advertencia mediante `showWarning` cuando una contraseña aceptada no satisface la política o no coincide.

### Lógica pura y presentación

- Son lógica no visual la validación del nombre no vacío, el conteo de valores sensibles, la comparación de contraseñas y su borrado del arreglo temporal.
- El diálogo, los controles, la localización y la coordinación del guardado pertenecen a UI.
- El conteo sensible depende del snapshot y de `UiStateSnapshot`; no debe copiar estado de controles en cachés.

## `handleEpochConverter`

### Entradas y llamadores

- `main-view-modern.fxml`: `epochMenuItem` llama al handler con `Shortcut+T`.
- `UiNavigationRegistry` registra `Epoch Converter`; `NavigationRouter` ejecuta el callback de `ModernMainController`, que llama a `handleEpochConverter`.
- La ruta también aparece en las pruebas de caracterización del `NavigationRouter` y en `KeyboardShortcutRegistry`.
- No aparece como comando explícito por nombre en `PaletteCommandCatalog`; la paleta navega por el catálogo de rutas.

### Estado y datos usados

- No usa campos `@FXML` de entrada; crea y muestra una ventana independiente.
- Consulta `mainPane.getScene()` solo para copiar las hojas de estilo.
- Al convertir, parsea segundos Unix, presenta el instante UTC y llama al contrato público `addToHistory`.
- Los detalles compartidos con historial son `Timestamp` y `Result`.

### Lógica pura y presentación

- Parseo y conversión de epoch a `Instant` son lógica pura y no requieren JavaFX.
- Ventana, layout, controles, manejo de errores y copia de estilos pertenecen a UI.

## `handleJsonFormatter`

### Entradas y llamadores

- `main-view-modern.fxml`: `jsonMenuItem` llama al handler con `Shortcut+J`.
- `UiNavigationRegistry` registra `JSON Formatter`; `NavigationRouter` usa el callback correspondiente del controlador.
- La ruta se incluye en las pruebas de `NavigationRouter` y en `KeyboardShortcutRegistry`.
- No aparece como comando explícito por nombre en `PaletteCommandCatalog`; la paleta navega por el catálogo de rutas.

### Estado y datos usados

- No usa campos `@FXML` de entrada; crea y muestra una ventana independiente.
- Consulta `mainPane.getScene()` solo para copiar las hojas de estilo.
- El botón construye un `Gson` con pretty printing, analiza el texto y presenta JSON o un error.
- Tras formatear con éxito llama a `addToHistory("JSON Formatter", ...)`; no incluye el texto en detalles.

### Lógica pura y presentación

- Parseo y serialización JSON con salida indentada son lógica pura.
- Ventana, layout, controles, manejo de errores y copia de estilos pertenecen a UI.

## Cohesión propuesta para la extracción

Los tres handlers no forman una sola responsabilidad: guardar una sesión requiere snapshot sensible, preferencias, contraseña, persistencia y un flujo de diálogo; Epoch y JSON son herramientas utilitarias efímeras, cada una con una transformación pura distinta y una ventana propia.

Se proponen dos coordinadores: `SaveSessionCoordinator` para el diálogo/guardado y `UtilityToolsCoordinator` para las dos ventanas pequeñas.

La lógica pura irá a utilidades sin JavaFX para conversión Epoch y formato JSON, con tests unitarios sin FXML.

Los coordinadores no capturarán controladores de módulos; las acciones y valores dinámicos se suministrarán con callbacks/proveedores.

## Pruebas a caracterizar antes de mover producción

- Sesión: guardar un nombre inventado, comprobar que aparece en el gestor y que no se persisten secretos en claro; comprobar nombre vacío y cancelación sin guardado.
- Epoch: navegar y comprobar host visible junto al contenido inicial de ventana.
- JSON: navegar y comprobar host visible junto al contenido inicial de ventana.
- Las pruebas que usan `~/.cryptocarver` deben aislar `user.home` en `target/test-home`, limpiar lo creado y restaurar los valores anteriores de `AppSettings`.
