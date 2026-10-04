# Process Designer fase 3: hallazgos previos a corregir

Primera ejecución de `ProcessDesignerConnectionCharacterizationUITest` sobre la base de fase 2 (`4987e73`), antes de extraer ni arreglar producción.

Comando: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=ProcessDesignerConnectionCharacterizationUITest`.

## Defecto encontrado antes de corregir

Al conectar una fuente `AES_KEY_GENERATE` al puerto `key` de `ENCRYPT`, la conexión y la marca `keyFromFlow=true` se crean correctamente y la fuente reutilizable permanece seleccionada. Undo quita el enlace, pero el nodo restaurado aún tiene `keyFromFlow=true`; el marker afirma que la clave llega por flujo aunque ya no existe el enlace.

La aserción que falla es la de `keyFromFlow` tras Undo. `connectToPort()` obtiene su definición anterior con `toDefinition()`, que copia las listas pero mantiene los mismos objetos nodo; luego muta la configuración del nodo para poner `keyFromFlow`. El registro de la orden, por tanto, ya observa esa marca en la supuesta definición anterior y Undo la repone.

Antes de llegar a esa aserción, pasaron las comprobaciones de inicio y movimiento del trazo temporal, atenuación de origen/puerto ocupado/puerto libre, cancelación al soltar fuera, restauración de opacidad y rechazo de un intento de ocupar una entrada ya enlazada sin añadir una orden undo. La ejecución se detuvo antes del resto de escenarios; aún no hay SHA caracterizador fijado.

El arreglo identificado antes de corregirlo fue tomar una instantánea profunda antes de mutar configuración. La nota inicial quedó en su commit antes del arreglo.

## Verificación del arreglo y preparación del SHA

El fallo se corrigió en el commit `a186257` haciendo `snapshot(toDefinition())` antes de mutar conexiones o `keyFromFlow`. Después del arreglo, Undo quita tanto enlace como marca, y Redo los repone. La ejecución enfocada completó todas las aserciones funcionales y llegó al digest: el valor esperado seguía intencionalmente como `PENDING`; el SHA calculado de las cinco filas UTF-8 unidas por `\n`, sin salto final, es `e8baa0c2d865ec4f5267eb2cc6aeecdaae884985cb7b6be4686151adb1b97310`.

La segunda ejecución también detectó una premisa incorrecta en el fixture de incompatibilidad: el nodo `HASH` acepta `TEXT_UTF8`, así que ese par sí podía conectarse. Se corrigió el fixture para usar como destino un `CONSOLE_INPUT`, que no tiene puertos de entrada. No se cambió ni ajustó ningún test existente ni ningún otro comportamiento de producción.

La siguiente ejecución fijará y volverá a verificar el digest.
