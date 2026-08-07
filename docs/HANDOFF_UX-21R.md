# UX-21R — handoff

## Alcance

Se cerró la política de redacción de `UiStateSnapshot` para History. Captura, filtrado al restaurar y limpieza previa a la reapertura comparten ahora la misma clasificación de campos sensibles.

La política cubre `kbpk`, `cvk`, `pvk`, `pin`, `pan`, `cvv`, `key`, `password`, `secret`, `private`, `certificate`, `iv`, `nonce`, `aad`, `salt`, `token`, `mac` y `signature`, además de los identificadores de entradas sensibles heredados. Los campos de resultados/reportes se excluyen del recipe de History.

Los selectores de algoritmo, formato, modo, tamaño y uso en `ComboBox`, `ChoiceBox` y `CheckBox` siguen siendo restaurables. Los valores `[REDACTED_SECRET]` no se rehidratan: los controles sensibles se limpian antes de aplicar la configuración segura.

Se añadieron pruebas explícitas para:

- redacción de `tr31KbpkExportField`, CVK y PVK;
- limpieza de esos campos y de resultados al reabrir History;
- restauración de selectores seguros;
- preservación literal de key blocks TR-31, hashes HEX, bytes Base64 y resultados JSON;
- cobertura headless de todas las familias de nombres sensibles.

## Pruebas

- `mvn -q test` ✅
- `git diff --check` ✅

No se ha creado ningún commit.

## Limitaciones y deuda

La cobertura que necesita controles JavaFX continúa siendo opt-in (`runUiTests=true`) y depende de Xvfb en CI. La política por nombres conserva algunos identificadores heredados (`input`, `payload`, `verify`, `tag`, etc.) para no rebajar la protección existente; si se introducen nuevos controles sensibles deben usar nombres incluidos en la política común.
