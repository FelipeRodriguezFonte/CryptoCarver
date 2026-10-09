# Fase 3 — fallos antes de fijar la caracterización

Un ajuste de la propia prueba antes de fijar el digest: la aserción `Serial: 81` no se cumplía porque el informe no imprime el número de serie en decimal. Se retiró esa línea de la transcripción (el número de serie queda fuera, como las fechas y las huellas) y la segunda ejecución fijó el digest. No fue un defecto de producción.

Comportamientos previos que la transcripción deja fijados tal como están, sin corregir:

- **Hallazgo:** `handleValidateTimestampToken` con un fichero que no es un token RFC 3161 no muestra error: escribe un informe y pone el estado "Timestamp token validated.", igual que con un token válido. El estado no distingue validación correcta de fallida. `handleInspectTimestampToken` sí muestra error con ese mismo fichero.
- Guardar una TSA predefinida (DigiCert, FreeTSA) muestra "TSA Saved" pero no escribe `customTsaUrl`.
- `handleSaveTSA` con una URL no válida, `handleTestTSA` y `handleSaveTSASavedProfile` comparten el título "TSA URL Error".
