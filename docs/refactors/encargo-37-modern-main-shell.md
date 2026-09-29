# Encargo 37: diálogos, atajos y diagnóstico de ModernMainController

Inventario preparado sobre `cf90f68` antes de cambiar producción. La clase tiene 3216 líneas.

## Llamadores y superficie

| Operación | Llamadores y entrada | Estado y campos FXML observados |
|---|---|---|
| `showInfo(title, message)` | Usos internos al ampliar resultados/tablas y vaciar la caché; `StatusReporter`/`ShellServices` equivalentes se inyectan a controladores de módulos. | `dialogService`, `mainPane` para la ventana propietaria. Bajo `test.mode` imprime `SHOW_INFO` y retorna. |
| `showWarning(title, message)` | `ShellServices` y flujo de sesiones guardadas; implementación concreta se pasa como callback. | `dialogService`, `mainPane`; `test.mode` imprime y retorna. |
| `showError(...)` | Implementa `StatusReporter`, usado por controladores y servicios de módulos; además tiene llamadas internas para errores de utilidades. | `inlineErrorPresenter`, `shellTextResolver`, `rootStackPane` con fallback a `mainPane`; actualiza el banner inline, no abre `Alert`. |
| Confirmaciones | Los confirmadores se obtienen mediante `DialogService`/coordinadores de funciones; no hay handler de confirmación general propio en esta clase. Diálogos de sesión conservan su controlador y resultado en el flujo de sesión. | `dialogService`, ventana derivada de `mainPane`; el banner/error inline comparte `inlineErrorPresenter`. |
| `handleShowKeyboardShortcuts()` | `shortcutsMenuItem` en `main-view-modern.fxml` (F1 y menú Help). No se encontraron invocaciones directas desde otros controladores ni tests por reflexión. | Construye VBox/GridPane/ScrollPane y usa `dialogService`; fuente de datos `KeyboardShortcutRegistry`. No muta estado compartido. |
| `handleDiagnostics()` | `diagnosticsMenuItem` en `main-view-modern.fxml`. | `mainPane`; obtiene resumen de pantalla, genera texto vía `AppDiagnostics`, crea `TextArea`, presenta `Alert` y puede copiar al portapapeles/actualizar `statusLabel` mediante `updateStatus`. |
| `writeDiagnosticsReport(Path,String)` | Solo test `ModernMainControllerDiagnosticsTest`; método estático package-private, escribe el contenido en el path indicado. No lo invoca el handler de Diagnostics. | Sin estado/JavaFX. |

`main-view-modern.fxml` enlaza `shortcutsMenuItem` con `#handleShowKeyboardShortcuts` y `diagnosticsMenuItem` con `#handleDiagnostics`. Estos handlers son privados/públicos según el contrato FXML/API ya existente. Los métodos de aviso/error son contratos públicos que usan controladores de módulos mediante el reporter compartido.

## Lógica pura frente a JavaFX

- `AppDiagnostics.report(displaySummary)` compone texto con versión, runtime, plataforma, locale y proveedores; explícitamente excluye material de claves, datos de entrada, credenciales y rutas. Es lógica sin JavaFX.
- `KeyboardShortcutRegistry` mantiene entradas y combinaciones declarativas. La conversión/display por plataforma se mantiene en su modelo. El catálogo y sus textos se pueden verificar sin cargar FXML.
- `writeDiagnosticsReport` escribe un `String` a un `Path` sin depender de JavaFX.
- `describePrimaryDisplay`, `TextArea`, botones, copiar al portapapeles y `updateStatus` dependen de JavaFX.
- La creación/estilo/propiedad y presentación de Alert/Dialog corresponde a `DialogService`; la futura coordinación del shell debe inyectar proveedores y no retener controladores de módulo.
- `showError` es pintado inline por `InlineErrorPresenter` sobre `rootStackPane` o `mainPane`; sus mensajes de test son instrumentación, no una alerta.

## Recorte de «Aceptar»: diagnóstico previo al arreglo

Los avisos informativos `showInfo` se delegan a `DialogService.info`, que crea `Alert(INFORMATION, detail, ButtonType.OK)`. `configure` añade `cc-dialog-pane` y `styles.css` carga `theme-light.css`/`theme-dark.css` junto con estilos base. `styles.css` solo importa tokens, tema claro y `components.css`; las reglas de diálogo cambian fondos y cabecera, pero no anchura del botón ni tamaño del `DialogPane`. La alerta normal no fija el tamaño de `DialogPane` ni aplica CSS propio a `ButtonType.OK`.

Por tanto, el recorte no lo causa un `ButtonType` custom ni una regla `.dialog-pane` de `styles.css`: «Aceptar» es el texto localizado del `ButtonType.OK`, y el botón nativo de JavaFX conserva su ancho calculado a partir del texto/insets. El pane de la alerta de contenido corto queda dimensionado por el contenido mínimo de su gráfico; bajo el tema de JavaFX, el ancho mínimo del botón y su padding no bastan para el texto localizado, y el skin recorta el label. No hay `minWidth`/`prefWidth` del botón ni del `DialogPane` que impida ese resultado. El test de caracterización medirá el bounds/layout del botón. Este inventario no cambia producción.
