# Fallos previos a fijar la caracterización de fase 6

Primera ejecución dirigida de `ProcessDesignerExecutionCharacterizationUITest` en el worktree `CryptoCarver-pd-3`, rama `codex/process-designer-3-v2`:

- `completeRunDryRunAndPaletteHaveStableTranscript`: la expectativa provisional del test decía `Completed`; la UI actual presenta `Completed successfully`. La ejecución sí completó los tres nodos. Se corregirá la expectativa del test para reflejar el texto observado; no es un defecto de producto.
- `suppliedSecretsStayProtectedAcrossProfilesAndPreflight`: las aserciones funcionales pasaron para `FULL_LAB`, `MASKED` y `REDACTED`: la clave sintética aparece solo en las superficies permitidas por `FULL_LAB`, el campo de contraseña se representa enmascarado, no aparece en estado/filas/telemetría/undo ni en historial/Shelf, y la clave queda enmascarada u omitida en traza y visor expandido. El SHA provisional de ceros falló como marcador deliberado; la transcripción medida fue `b085d9994094d4a16df9437cea802c90301a13e6ee5a94e962507a2bd85c084b`.
- La primera normalización de excepciones convirtió el nombre `NoSuchAlgorithmException` pero dejó el resto de la misma línea, incluido un mensaje que puede variar entre JDK/proveedores. Antes de fijar el digest, se ampliará esa normalización para sustituir el sufijo completo por `<jdk-exception>` según el requisito de portabilidad.

No se encontró fuga de la clave de prueba ni fallo de comportamiento del producto en esta ejecución. El digest no se fija hasta corregir la expectativa de UI y la normalización.

Segunda ejecución dirigida, con la etiqueta de estado ajustada y la normalización JDK ampliada:

- `completeRunDryRunAndPaletteHaveStableTranscript` alcanzó el digest provisional y reveló que `normalizeDryRun()` sustituye IDs simples en cualquier parte del texto: transformó por accidente etiquetas visibles como `Console input` y `Console output`. El ID del preset es estable, así que se anotó esta discrepancia del helper antes de corregirlo. La transcripción también muestra `HASH/INCOMPLETE` en dry-run, aunque el mismo preset acaba de ejecutarse con éxito; se comprobará la semántica del validador antes de decidir si es un defecto de producto o una expectativa incorrecta.
- El transcript de perfiles/preflight pasó todas las aserciones de comportamiento y privacidad. Su digest provisional con la excepción normalizada es `c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1`.

Los dos digests de esta sección se observaron con marcador cero deliberado; aún no están fijados.

Confirmación del dry-run divergente, antes de cualquier cambio de código:

- `ProcessDesignerController.handleLoadSha256Preset()` conecta `CONSOLE_INPUT` a `HASH` con target `payload` (la inicialización base usa target nulo). `HashNodeHandler.inputPorts()` declara el único puerto obligatorio `input`.
- `ProcessEngine.validate()` ya normaliza `payload`/target nulo al único puerto `input`; por eso la ejecución completa acepta el grafo.
- `ProcessValidator.validateSteps()` crea el binding como `payload` y comprueba `input` antes de llamar a `ProcessEngine.validate()`, por lo que el resumen da `HASH/INCOMPLETE` y evita esa validación. La transcripción fijará el resultado corregido como `READY` para que el dry-run y la ejecución concuerden.

Esto es un defecto preexistente de validación detectado por la caracterización. Se reparará en un commit propio y se añadirá una prueba enfocada al alias de puerto; esta nota antecede a ambos cambios.
