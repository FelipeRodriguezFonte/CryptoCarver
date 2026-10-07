# Fase 6: caracterización antes de extraer

Primera ejecución sobre ODA original: exit 0, 1 informe / 1 prueba / 0 fallos, errores u omitidas. Ningún fallo observado antes de fijar SHA-256 `4e43fad065dbf13eb60bb6c20e0ee54145a83d9ef29873643aa66f09fce66981`. Transcripción UTF-8 sin salto final: target/emv-oda-75-transcript.txt; log: target/emv6-characterization.log.

El test cubre emisión de tarjeta desechable, recuperación de certificados, SDA/DDA/CDA, static data alterada, clear y conservación de Shelf/historial. No se fijan RSA aleatorio, caducidad ni textos de excepción. Se fija forma/población de controles y resultados PASSED/FAILED de aplicación, con aserciones independientes. Se retiene una clave IMK inventada para comprobar que la receta ODA y las demás superficies no la filtran bajo MASKED/REDACTED; las claves privadas RSA generadas por el handler no se almacenan en UI.
