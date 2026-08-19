# UX-21 — handoff

## Alcance

Se endurecieron los flujos de reutilización de datos técnicos en History, Clipboard Shelf, Compare Results, Batch Runner y TR-31.

- History reabre el módulo correcto, expande el destino y restaura únicamente configuración segura. Las claves, PIN, IV, nonce, contraseñas, certificados privados y resultados no se restauran.
- Clear History mantiene la confirmación segura existente, con excepción controlada para `test.mode`, y no toca Clipboard Shelf.
- Clipboard Shelf incorpora destinos XML Security, WSS/SOAP, Payments PIN Block y TR-31. Valida compatibilidad por formato, conserva el valor exacto y muestra el destino expandido. Los formatos incompatibles generan un error localizado y accionable sin conversión.
- Compare Results tiene estado vacío localizado, reset explícito, carga desde Shelf y preservación de valores técnicos para comparación.
- Batch Runner queda limitado en la UI a transformaciones data-only. CSV/JSONL rechaza nombres de columnas de secretos o claves; las entradas originales permanecen separadas de los resultados generados. Run, dry-run, cancel, export y reset tienen feedback localizado.
- TR-31 recibe rutas desde rail/Keys, inyección exacta desde Shelf y acciones Clear/Reset Defaults sin alterar key blocks, KSN, algoritmos, KCV, bytes ni resultados.
- Se añadieron contratos headless y pruebas JavaFX opt-in para History, Shelf, Compare Results, Batch Runner y TR-31.

## Pruebas

Ejecutadas correctamente:

- `mvn -q test`
- `xmllint --noout src/main/resources/fxml/generic.fxml src/main/resources/fxml/compare_results.fxml src/main/resources/fxml/keys.fxml`
- `git diff --check`

La suite JavaFX opt-in se ejecuta con el workflow existente mediante `bash scripts/run-ui-tests.sh`, que usa `xvfb-run`, `-DrunUiTests=true` y Prism software. No se pudo ejecutar localmente en este entorno macOS porque `xvfb-run` no está instalado; la ruta CI ya dispone de Xvfb.

## Limitaciones UI

- Las pruebas live dependen de Xvfb y de la carga completa de JavaFX/FXML.
- Los destinos XML/WSS/TR-31 aceptan texto técnico; Payments exige HEX. La compatibilidad se rechaza explícitamente cuando el formato no corresponde.
- La UI conserva código legado de cifrado de registros en `GenericController`, pero sus opciones permanecen fuera del selector Batch Runner data-only.

## Deuda

- Sustituir gradualmente textos estáticos heredados de FXML por claves del catálogo común.
- Añadir cobertura live para cada combinación de locale y para errores de cada selector de formato.
- Extraer la matriz de destinos Shelf a un servicio compartido si aparecen nuevos módulos consumidores.
- Revisar la ruta de migración del código legado de lotes criptográficos para evitar que vuelva a exponerse en la UI.

## Módulos restantes

No quedan módulos del alcance UX-21 pendientes de implementación. Quedan como seguimiento la ejecución Linux de la suite JavaFX en CI y la deuda UI indicada arriba.

No se ha creado ningún commit.
