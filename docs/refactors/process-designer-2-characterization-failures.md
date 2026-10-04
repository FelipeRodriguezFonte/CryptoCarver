# Process Designer fase 2: hallazgos previos a fijar el digest

Primera ejecución de `ProcessDesignerSelectionCharacterizationUITest` sobre la base solicitada `26abc5f0a4f6f7b3504ea864f2e8127659d2fee2`, antes de extraer código.

Comando: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=ProcessDesignerSelectionCharacterizationUITest`.

## Resultado antes de fijar SHA-256

Las aserciones de comportamiento pasaron. La ejecución terminó con el fallo deliberado de bootstrap en la aserción de digest: el valor esperado era `PENDING` y el digest calculado de las filas unidas por `\n` (UTF-8, sin salto final) fue `76ea24e9f0beb0df58d8ad4b8e0fd978e7968ac2086c3d659173bd6c22d88fdd`. El SHA todavía no se cambió en el test en este commit.

No se encontró defecto de producto en esta caracterización: la selección simple desactiva conectar; dos nodos compatibles muestran un menú con cuatro puertos; seleccionar por ID conserva el par cuando el ID existe y deja la selección intacta si es nulo o desconocido; elegir una conexión y limpiar selección actualiza sus controles; el inspector reconstruye la clave sintética en un `PasswordField`. Con `MASKED`, la transcripción de ejecución muestra solo el marcador enmascarado; con `REDACTED`, omite la línea de clave. La clave no aparece en etiquetas del inspector, el grafo persistido, pilas undo/redo ni barra de estado. Shelf e historial externo quedaron sin cambios.

La compilación inicial del test se detuvo por una excepción checked no envuelta en el helper del fixture; se corrigió únicamente el helper de prueba antes de obtener este resultado. No afectó a producción ni a las aserciones de caracterización.

Este informe queda registrado antes de reemplazar `PENDING` por el digest calculado.
