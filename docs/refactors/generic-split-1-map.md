# GenericController, fase 1: Batch Runner y conversión de ficheros

`ui/GenericController.java` tenía 2421 líneas. Esta fase saca dos paneles:

- `BatchRunnerCoordinator`: entrada CSV o JSON Lines, ejecución en segundo plano con progreso y
  cancelación, simulación (dry run), exportación del informe y borrado de la clave tras cada
  ejecución. El ejecutor sustituible para tests (`setBatchRunnerExecutorForTesting`) se
  mantiene; los tests leen la tarea y el informe con accesores del paquete.
- `FileConversionCoordinator`: conversión binario/hex/Base64/texto, comparación, hash y vista
  previa en streaming.
- Código muerto eliminado: `initializeFileConverter` y `setFileComparePathField`, sin llamadores.
- `GenericController` queda en unas 1800 líneas. Caracterizado antes con
  `BatchAndFileCharacterizationUITest`.

## Fallos destapados y corregidos

- «Convertir» no hacía nada sin ruta de salida: ni la vista previa en pantalla que el código
  sabe hacer ni los errores (fichero inexistente, hex no válido, ruta vacía). Se veía el
  resultado anterior. Ahora la ruta de salida solo hace falta para escribir binario.
- Cada conversión se publicaba dos veces en el histórico, y la segunda aunque fallara.
- La entrada hex aceptaba caracteres no hexadecimales y producía bytes basura; ahora da error.
- Un fallo deja el área de resultado vacía en lugar del resultado anterior, y un fichero
  inexistente dice «File not found».

## Observaciones sin cambio

- Las operaciones «Encrypt Record» y «Decrypt Record» del Batch Runner no están en la lista de
  operaciones (el catálogo es deliberadamente solo de datos), así que su código no es accesible
  desde la interfaz.
