# Encargo 37: diálogos, atajos y diagnóstico de ModernMainController

Inventario preparado sobre `cf90f68` antes de cambiar producción. La clase tiene 3216 líneas.

## Llamadores y superficie

| Operación | Llamadores y entrada | Estado y campos FXML observados |
|---|---|---|
| `showInfo(title, message)` | Llamadas internas de ampliación de resultados/tablas y de vaciado de caché. No forma parte de `StatusReporter`; no se encontró un `ShellServices` común con este método. | `dialogService`, `mainPane` para la ventana propietaria. Bajo `test.mode` imprime `SHOW_INFO` y retorna. |
| `showWarning(title, message)` | Se pasa como callback a `SessionTrailCoordinator` y al flujo de contraseña de sesiones guardadas. | `dialogService`, `mainPane`; bajo `test.mode` imprime `SHOW_WARNING` y retorna. |
| `showError(...)` | Contrato `StatusReporter`, inyectado por `connectShellServices` en JOSE, COSE, Wallet, Generic y sus paneles anidados; también se usa desde utilidades del shell. | `inlineErrorPresenter`, `shellTextResolver`, `rootStackPane` con fallback a `mainPane`; pinta el banner inline, no abre `Alert`. |
| Confirmaciones y formularios | Las confirmaciones de borrar/limpiar viven en `SavedSessionsCoordinator` y `SessionTrailCoordinator` y usan `DialogService`. `handleSaveSession` conserva su `Dialog<ButtonType>` y el diálogo de contraseña en el propio controlador. | Los coordinadores reciben el `DialogService` y el proveedor de ventana. Los diálogos de guardar sesión leen estado del shell y no se mueven en esta extracción. |
| `handleShowKeyboardShortcuts()` | `shortcutsMenuItem` en `main-view-modern.fxml` (F1 y menú Help). No se encontraron invocaciones directas desde otros controladores ni tests por reflexión. | Usa `mainPane` como dueño; la lista sale de `KeyboardShortcutRegistry`. No muta estado compartido. |
| `handleDiagnostics()` | `diagnosticsMenuItem` en `main-view-modern.fxml`. | Usa `mainPane`, el resumen de pantalla y `updateStatus`; puede copiar el reporte al portapapeles. |
| `writeDiagnosticsReport(Path,String)` | Solo test `ModernMainControllerDiagnosticsTest`; método estático package-private, escribe el contenido en el path indicado. No lo invoca el handler de Diagnostics. | Sin estado/JavaFX. |

`main-view-modern.fxml` enlaza `shortcutsMenuItem` con `#handleShowKeyboardShortcuts` y `diagnosticsMenuItem` con `#handleDiagnostics`. Los campos FXML directos de esos handlers son `mainPane`, `shortcutsMenuItem` y `diagnosticsMenuItem`; los métodos públicos de aviso/error no son handlers FXML. `connectShellServices` es un método, no existe una clase `ShellServices`: para errores inyecta `StatusReporter`; para navegación y estado usa otros contratos y callbacks.

## Lógica pura frente a JavaFX

- `AppDiagnostics.report(displaySummary)` compone texto con versión, runtime, plataforma, locale y proveedores; explícitamente excluye material de claves, datos de entrada, credenciales y rutas. Es lógica sin JavaFX.
- `KeyboardShortcutRegistry` mantiene entradas y combinaciones declarativas. La conversión/display por plataforma se mantiene en su modelo. El catálogo y sus textos se pueden verificar sin cargar FXML.
- `writeDiagnosticsReport` escribe un `String` a un `Path` sin depender de JavaFX; tras la extracción delega a `DiagnosticsReportBuilder`.
- `describePrimaryDisplay`, `TextArea`, botones, copiar al portapapeles y `updateStatus` dependen de JavaFX.
- `ShellDialogCoordinator` posee el montaje y pintado de avisos informativos, atajos, About y diagnóstico. Recibe `DialogService` y un `Supplier<Window>`; no guarda ni captura controladores de módulo.
- `showError` es pintado inline por `InlineErrorPresenter` sobre `rootStackPane` o `mainPane`; sus mensajes de test son instrumentación, no una alerta.

## Recorte de «Aceptar»: causa comprobada antes del arreglo

Los avisos informativos `showInfo` se delegan a `DialogService.info`, que crea `Alert(INFORMATION, detail, ButtonType.OK)`. `configure` añade `cc-dialog-pane` y carga `styles.css` más el tema activo. `styles.css` importa `components.css`, donde `.button` da padding horizontal de 15 px por lado; el skin de JavaFX `ButtonBar` conserva un mínimo de 70 px. No había una regla de `min-width` para el botón OK del diálogo.


La medición del test antes del arreglo fue: texto localizado «Aceptar», ancho natural de botón 75,018 px, ancho renderizado 76 px y `ButtonBar` mínimo 70 px. El ancho final se redondeaba y dejaba menos de 1 px de holgura. Por eso el label quedaba al borde y podía cortarse. No intervenían un `ButtonType` personalizado ni las reglas de fondo/cabecera `.dialog-pane`. La caracterización exigió dos píxeles de margen adicional; el primer resultado fue 4 pruebas, 3 aprobadas y solo este caso en rojo.

## Caracterización, extracción y reparación

- El informe de `AppDiagnostics` ya era una clase pura; se añadió `DiagnosticsReportBuilder` sin JavaFX como entrada del coordinador y para escribir el reporte. El catálogo de atajos ya residía en `KeyboardShortcutRegistry`, también puro.
- `ModernMainController` conserva sus contratos y handlers. Los handlers de atajos, About y diagnóstico, y los avisos públicos, delegan al `ShellDialogCoordinator`.
- La reparación añade `cc-info-dialog-pane` únicamente al Alert informativo y establece 80 px mínimos para sus botones en el bloque final `Legibility overrides` de `styles.css`. Se usa CSS porque el `ButtonBar` crea/recalcula el tamaño de sus botones al montar el skin; asignar `Button.setMinWidth` antes de mostrar el diálogo no sobrevive a ese cálculo. No se cambió FXML.
- Capturas `ComputedStyleSnapshotTool`: `Hashing` con `theme-light.css` y `theme-dark.css`, `user.home` limpio. El diff antes/después de Parte C fue vacío en ambos temas. El diff de la captura shell después de Parte D también fue vacío; la regla nueva solo coincide dentro del Alert informativo.
- La ejecución dirigida posterior pasó: caracterización UI 4/4; `DiagnosticsReportBuilderTest` 2/2; contrato anterior `ModernMainControllerDiagnosticsTest` 1/1; `DialogServiceTest` 3/3.
- Suite completa `mvn -o -q test`: 2412 pruebas, 0 fallos, 0 errores, 1 omitida; aproximadamente 3 min 40 s. La clase UI nueva se ejecutó por separado con `-DrunUiTests=true`.
- `ModernMainController.java`: 3216 líneas antes; 3146 después de Parte C y D.
- No se hizo prueba manual.

Commits previstos para esta rama: inventario, `feeb057`; caracterización previa, `6026d78`; extracción, `a38c09e`; reparación, `d17a905`; este informe se confirma en el commit de documentación.
