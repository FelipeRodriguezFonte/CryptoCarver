# UX-22R — handoff

## Corrección

Se corrigió el fallback de feedback de TR-31 en `KeysController`: cuando no existe `mainController`, los errores de importación/análisis (`tr31KeyBlockField`, `tr31KbpkImportField`) se muestran en `tr31ImportResultArea`; los errores de exportación siguen usando `tr31ExportResultArea`. Si una variante legacy de FXML sólo carga una de las áreas, se usa la disponible como fallback final.

El mensaje continúa pasando por la redacción de feedback existente y no altera key blocks, KBPK, KSN, bytes ni resultados técnicos.

## Prueba

- `Ux22RHeadlessTest` verifica la clasificación de campos de importación/exportación sin `@Tag("ui")`.

No se ha creado commit ni se ha hecho push. Los archivos ajenos indicados permanecen fuera del cambio.
