# Fallos de caracterización de fase 5

La primera ejecución de `ProcessDesignerPreflightCharacterizationUITest` terminó antes de invocar `handleRunProcess`: el fixture asignó un valor de algoritmo desconocido y después llamó a `handleSaveNodeSettings()`. Ese método redibuja el canvas; al crear los puertos, el handler de cifrado vuelve a validar el algoritmo y lanza `IllegalArgumentException: Unsupported encryption algorithm: SYNTHETIC_UNKNOWN_CIPHER`.

El fallo no alcanzó las aserciones del presenter ni demuestra un defecto en `showPreflightFailure`. La prueba debe conservar el valor sintético en el control y activar la ruta real de Run, que guarda la selección mediante `saveSelectedNodeSettings()` y continúa al chequeo preflight sin redibujar. Se registró este fallo antes de cambiar el fixture o fijar el digest.

Con el fixture corregido, las aserciones funcionales pasaron para los tres casos (`SYNTHETIC_UNKNOWN_CIPHER`, AAD con CBC e IV con ECB) bajo `MASKED` y `REDACTED`. La ejecución solo falló por el marcador provisional de digest: SHA-256 observado antes de fijarlo, `1234b586aacd1d973763894b18878d474d46a5849630a6a8e3b061ff0137cf74` (64 caracteres). No se detectó un defecto de producto en la presentación preflight ni una fuga del secreto sintético.
