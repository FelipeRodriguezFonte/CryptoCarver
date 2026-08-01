# HANDOFF_UX-15B

## Alcance terminado

Se localizó progresivamente la superficie visible de:

- Process Designer: título, ayuda, acciones, inspector, tablas de ejecución, estados y cancelación.
- History: filtros, acciones, columnas, estado de operaciones y navegación de exportación.
- Clipboard Shelf: filtros, estado vacío, acciones, tooltips, selección múltiple y apertura de Compare Results.
- Compare Results: encabezados, propiedades, exportación, estado sin diferencias y errores accionables.
- Batch Runner únicamente dentro de Generic: ayuda, controles, progreso, simulación, cancelación, exportación y errores.

Se reutilizaron `I18nService`, `ModuleI18n` y `ModuleTextCatalog`. No se introdujo un sistema paralelo. Los textos estáticos se enlazan desde la raíz FXML y los textos creados dinámicamente usan `I18nService` directamente.

Los resultados técnicos y los datos almacenados no se localizan: valores HEX/Base64/PEM, hashes, bytes, algoritmos, IDs, recetas, JSON/XML y contenido del histórico o Shelf se conservan como datos. Los textos envolventes traducidos reciben esos valores como argumentos. La política de secretos y las restricciones de selección de comparación/exportación no se modificaron.

## Archivos cambiados

- `src/main/java/com/cryptocarver/ui/ModuleI18n.java`
- `src/main/java/com/cryptocarver/ui/ModuleTextCatalog.java`
- `src/main/java/com/cryptocarver/ui/ProcessDesignerController.java`
- `src/main/java/com/cryptocarver/ui/HistoryController.java`
- `src/main/java/com/cryptocarver/ui/ClipboardShelfController.java`
- `src/main/java/com/cryptocarver/ui/CompareResultsController.java`
- `src/main/java/com/cryptocarver/ui/GenericController.java`
- `src/main/resources/fxml/process_designer.fxml`
- `src/main/resources/fxml/clipboard_shelf.fxml`
- `src/main/resources/fxml/compare_results.fxml`
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_es.properties`
- `src/test/java/com/cryptocarver/ui/ResultFlowI18nTest.java`
- `src/test/java/com/cryptocarver/ui/ResultFlowsLiveI18nUITest.java`
- `docs/HANDOFF_UX-15B.md`

`history.fxml` ya tenía una raíz identificable y no necesitó cambios. No se incluyeron Payments, JOSE, PQC, XML/WSS ni otros módulos fuera del bloque.

## Pruebas y verificaciones

Pasó (`exit 0`):

```text
/opt/homebrew/bin/mvn -q -Dtest=ResultFlowI18nTest,ResultFlowsLiveI18nUITest,ModuleTextCatalogTest,I18nServiceTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest test
```

La salida solo contiene los diagnósticos esperados de fallback de `I18nService` para claves/bundles inexistentes.

Pasó (`exit 0`) para cada archivo:

```text
xmllint --noout src/main/resources/fxml/process_designer.fxml
xmllint --noout src/main/resources/fxml/history.fxml
xmllint --noout src/main/resources/fxml/clipboard_shelf.fxml
xmllint --noout src/main/resources/fxml/compare_results.fxml
```

Pasó (`exit 0`):

```text
git diff --check
```

El repositorio imprime además el diagnóstico preexistente del daemon fsmonitor: `error: fsmonitor_ipc__send_query: unspecified error on '.git/fsmonitor--daemon.ipc'`; no produjo errores de whitespace.

El comando focal que incluye directamente las suites heredadas de UX-12/UX-13 terminó con error de entorno JavaFX:

```text
/opt/homebrew/bin/mvn -q -Dtest=ResultFlowI18nTest,ResultFlowsLiveI18nUITest,ModuleTextCatalogTest,I18nServiceTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest,ProcessDesignerControllerTest,ProcessDesignerUX12UITest,ClipboardShelfUX13UITest test
```

Resultado literal relevante:

```text
Loading library prism_sw ... Operation not permitted
Graphics Device initialization failed ... no suitable pipeline found
java.lang.RuntimeException: No toolkit found
Tests run: 43, Failures: 0, Errors: 2, Skipped: 6
```

Los dos errores proceden de la inicialización JavaFX sin pipeline/pantalla, no de una aserción de UX-15B. La suite nueva `ResultFlowsLiveI18nUITest` queda marcada como UI opt-in (`-DrunUiTests=true`) por la misma limitación.

## Deuda y limitaciones para UX-15B/continuación

- Persisten textos dinámicos secundarios en History, Shelf y Process Designer —por ejemplo algunos tooltips de disponibilidad, diálogos legacy y mensajes técnicos de detalle— que no son necesarios para los flujos prioritarios, pero deben consolidarse en la siguiente pasada.
- Las etiquetas de algoritmos, formatos, clasificaciones, nombres de operaciones, rutas y contenido técnico permanecen deliberadamente literales.
- La validación visual en vivo requiere ejecutar en una sesión JavaFX con pantalla y caché Prism escribible; el test de contrato XML y los tests de catálogo sí son ejecutables sin pantalla.
- UX-15C puede completar la cobertura de diálogos legacy, accessibleText generado después de la carga, estados de tabla y los módulos restantes sin ampliar este bloque.

No se hizo commit.
