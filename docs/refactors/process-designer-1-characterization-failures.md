# Process Designer fase 1: hallazgos previos a la extracción

Primera ejecución de la nueva caracterización sobre `6bc64a63164551c00274d0be25ccd6ba808d5ace`, antes de cambiar producción. En esa ejecución `HEAD` y `main` resolvían al mismo commit, por lo que los hallazgos corresponden a la base `main` solicitada.

Comando: `mvn -q -Plow-cpu -Dtest=ProcessDesignerUndoTidyCharacterizationUITest test`.

Las dos suites existentes se ejecutaron por separado sobre esa misma base y pasaron: `mvn -q -Plow-cpu -Dtest=ProcessDesignerUndoRedoTest test` y `mvn -q -Plow-cpu -Dtest=ProcessDesignerDuplicatePortUITest test`.

## Fallos observados antes de corregir

1. **Undo de ordenar no restaura coordenadas.** Tras ordenar tres nodos que estaban en `470,330`, `110,510` y `720,160`, undo dejó el primero en `60,80`. `handleTidyLayout()` entregaba a `recordStateChange()` un `toDefinition()` superficial; sus nodos seguían siendo los objetos vivos que se acababan de mover.
2. **Duplicar conserva marcadores de estado que la copia no tiene.** Al duplicar un nodo `ENCRYPT` con puertos `payload` y `key` conectados, la copia no recibe enlaces (correcto), pero hereda `keyFromFlow=true` (incorrecto). Al duplicar un nodo con la clave sensible introducida en el inspector, también hereda `keyFromSecrets=true` sin recibir el `char[]` de `transientSecrets`. El primero declara una entrada por flujo inexistente; el segundo declara un secreto de sesión que la copia no conserva.

## Observación del fixture, no fallo de producto

La primera ejecución del escenario combinado de borrado usó una referencia al nodo anterior a undo/redo. La restauración crea objetos nuevos; la prueba debía volver a buscar el nodo por id antes de borrarlo. Se corregirá el fixture y se volverá a ejecutar contra la misma base antes de cambiar producción.

La repetición, todavía sobre el mismo commit base, confirmó que el escenario de añadir/mover/conectar/borrar y el límite de 60 pasan; el escenario de secreto confirmó que el valor sintético no aparece en telemetría ni barra de estado bajo `MASKED` y `REDACTED`. La aserción adicional de duplicación detectó el marcador `keyFromSecrets` descrito arriba. Los SHA estaban temporalmente marcados como pendientes para fijarlos después de corregir los hallazgos de producto.

## Regresión encontrada durante la extracción

La primera pasada del `ProcessUndoRedoCoordinator` llamó a `redo()` al registrar una instantánea. Eso recargaba el canvas después de guardar cada configuración y borraba secretos transitorios, aunque registrar una edición solo debe apilarla. La suite existente `ProcessDesignerUndoRedoTest` falló al rehacer `updated-value`; la nueva caracterización también falló al verificar la conexión recién creada y el secreto transitorio. Esta regresión no estaba en `main`: la introdujo la extracción y se corregirá separando el registro de una instantánea de la ejecución pública de un comando.

Corregido antes de cerrar el primer commit: `recordStateChange()` ahora apila directamente la instantánea; solo `executeCommand()` ejecuta primero un comando público. `ProcessDesignerUndoRedoTest` y las caracterizaciones de añadir/mover/conectar/borrar, límite, layout y redacción pasan con esa separación. Queda pendiente únicamente la aserción de que un duplicado no herede `keyFromFlow`/`keyFromSecrets`.
