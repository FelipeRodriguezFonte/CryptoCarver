# UX-21R2 — handoff

## Alcance

Se amplió la política común de `UiStateSnapshot` con el token `cert`, manteniendo `certificate`. Por ello, `certInputArea`, `certIssueCaKeyArea` y controles equivalentes se redactan durante la captura de History, se limpian antes de restaurar y nunca se rehidratan desde un recipe.

Los selectores seguros de certificados y algoritmos/formato/tamaño siguen siendo restaurables en `ComboBox`, `ChoiceBox` y `CheckBox`.

## Pruebas

- `Ux21HeadlessTest` cubre `kbpk`, `cvk`, `pvk`, `cert`, PIN/PAN/CVV y selectores seguros sin `@Tag("ui")`.
- `UiStateSnapshotTest` mantiene la cobertura JavaFX opt-in de captura, limpieza y restauración, ampliada con campos de certificados.
- `mvn -q test` ✅
- `git diff --check` ✅

No se ha creado ningún commit.

## Limitaciones

La comprobación con controles JavaFX continúa siendo opt-in y depende de Xvfb en CI. La política por nombres conserva las reglas heredadas para entradas/payloads y otros identificadores genéricos sensibles.
