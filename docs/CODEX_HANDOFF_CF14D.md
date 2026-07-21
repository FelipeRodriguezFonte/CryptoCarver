# CF-14D — Recent Operations modularizado

## Alcance realizado

- Se integró `history.fxml` como componente FXML del contenido principal mediante `fx:include`.
- `HistoryController` gestiona la tabla de operaciones, detalles clasificados, selección múltiple, comparación, recetas, informes Markdown, visibilidad de secretos y limpieza.
- **Export Report** genera un informe Markdown de la operación seleccionada y aplica exactamente la política de secretos elegida (`REDACTED`, `MASKED` o `FULL_LAB`).
- `ModernMainController` conserva el rol de shell: provee el `HistoryManager`, restaura una operación seleccionada y mantiene el historial compacto del inspector. `HistoryController` se comunica con él sólo a través de `OperationNavigator`, sin conocer su implementación concreta.
- La navegación **Recent Operations** muestra ahora el componente modular, en lugar de construir la pantalla dinámicamente.

## Archivos relevantes

- `src/main/resources/fxml/main-view-modern.fxml`
- `src/main/resources/fxml/history.fxml`
- `src/main/java/com/cryptoforge/ui/HistoryController.java`
- `src/main/java/com/cryptoforge/ui/OperationNavigator.java`
- `src/main/java/com/cryptoforge/ui/ModernMainController.java`
- `src/test/java/com/cryptoforge/ui/ModernMainControllerFxmlStaticTest.java`
- `src/test/java/com/cryptoforge/ui/ModernMainControllerUITest.java`

## Verificación

- `xmllint --noout src/main/resources/fxml/main-view-modern.fxml src/main/resources/fxml/history.fxml`
- `mvn -q -Dtest=ModernMainControllerFxmlStaticTest test`
- `mvn -q -Prelease-experimental -DrunUiTests=true -Dtest=ModernMainControllerUITest test`
- `git diff --check`

La prueba UI carga el FXML completo, comprueba la ruta **Recent Operations**, verifica la inyección de `historyViewController` y confirma que una operación publicada aparece en la tabla del módulo.

## Deuda conocida

El constructor dinámico histórico anterior permanece como `showLegacyHistoryView()` sin rutas activas, como compatibilidad temporal. La UI moderna utiliza exclusivamente `HistoryController`.
