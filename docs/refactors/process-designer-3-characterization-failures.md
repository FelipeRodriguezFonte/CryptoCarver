# Process Designer fase 3: hallazgos previos a corregir

Primera ejecución de `ProcessDesignerConnectionCharacterizationUITest` sobre la base de fase 2 (`4987e73`), antes de extraer ni arreglar producción.

Comando: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=ProcessDesignerConnectionCharacterizationUITest`.

## Defecto encontrado antes de corregir

Al conectar una fuente `AES_KEY_GENERATE` al puerto `key` de `ENCRYPT`, la conexión y la marca `keyFromFlow=true` se crean correctamente y la fuente reutilizable permanece seleccionada. Undo quita el enlace, pero el nodo restaurado aún tiene `keyFromFlow=true`; el marker afirma que la clave llega por flujo aunque ya no existe el enlace.

La aserción que falla es la de `keyFromFlow` tras Undo. `connectToPort()` obtiene su definición anterior con `toDefinition()`, que copia las listas pero mantiene los mismos objetos nodo; luego muta la configuración del nodo para poner `keyFromFlow`. El registro de la orden, por tanto, ya observa esa marca en la supuesta definición anterior y Undo la repone.

Antes de llegar a esa aserción, pasaron las comprobaciones de inicio y movimiento del trazo temporal, atenuación de origen/puerto ocupado/puerto libre, cancelación al soltar fuera, restauración de opacidad y rechazo de un intento de ocupar una entrada ya enlazada sin añadir una orden undo. La ejecución se detuvo antes del resto de escenarios; aún no hay SHA caracterizador fijado.

El arreglo debe tomar una instantánea profunda antes de mutar configuración. Se registrará por separado antes de extraer el coordinador.
