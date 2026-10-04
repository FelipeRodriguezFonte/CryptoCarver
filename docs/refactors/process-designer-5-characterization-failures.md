# Fallos de caracterización de fase 5

La primera ejecución de `ProcessDesignerPreflightCharacterizationUITest` terminó antes de invocar `handleRunProcess`: el fixture asignó un valor de algoritmo desconocido y después llamó a `handleSaveNodeSettings()`. Ese método redibuja el canvas; al crear los puertos, el handler de cifrado vuelve a validar el algoritmo y lanza `IllegalArgumentException: Unsupported encryption algorithm: SYNTHETIC_UNKNOWN_CIPHER`.

El fallo no alcanzó las aserciones del presenter ni demuestra un defecto en `showPreflightFailure`. La prueba debe conservar el valor sintético en el control y activar la ruta real de Run, que guarda la selección mediante `saveSelectedNodeSettings()` y continúa al chequeo preflight sin redibujar. Se registró este fallo antes de cambiar el fixture o fijar el digest.
