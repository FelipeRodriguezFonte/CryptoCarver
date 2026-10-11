# Revisión B05.1 — entrega 02

Fecha: 2026-10-10. Turno: `01a126a1-d9ed-78b1-afec-991706ba9d2c`.

**Dictamen: requiere correcciones acotadas; B05.1 todavía no aprobado. B05.2/B05.3 y B06 esperan.**

La entrega mejora las ocho fichas asignadas: textos ES/EN específicos, referencias, opciones derivadas de consumidores, campos reales y fixtures mediante handlers. Las fichas B05 restantes ahora están incompletas. Compressed Hex representa entrada/salida combinadas, publica dirección al ejecutar y permite consultar ambos sentidos. Se inspeccionaron el encargo, inventario/matriz, catálogo, recursos, coordinadores y pruebas. Los hallazgos previos de Batch/nodos/ventanas restantes se mantienen para sus subentregas; no se atribuyen a este cierre acotado.

## R6 — acción ejecutada y ayuda de campos compartidos

Sonda independiente en shell real, 1800 px, con preferencias aisladas: abrir Manual Conversion, seleccionar formatos Hex y ejecutar Endian con `01020304`, después consultar directamente el `?` de la entrada. Resultado: `AFTER_EXECUTING_ENDIAN_INPUT_HELP=convert`. No se consulta antes el `?` de la acción Endian. Los handlers públicos delegan en ManualConversionCoordinator sin registrar la acción ejecutada; los bindings dinámicos conservan la última acción consultada o usan Convertir por defecto.

La prueba entregada consulta primero `endianActionHelp` y luego el campo, por lo que no cubre este caso. Corregir el seguimiento de acciones reales en Manual y cotejar el mismo patrón en File Conversion y Check Digits, donde las pruebas también preseleccionan ayuda antes del campo. Ejecutar o intentar una acción debe dejar la ayuda de la entrada/salida coherente con esa acción sin abrir ayuda cuando el modo esté desactivado. No cambiar parsers, resultados ni parámetros. Añadir regresión de ejecución→consulta directa de campo, cambio a otra acción y retorno de ruta. Conservar selección de acción explícita del usuario hasta que exista una acción real posterior.

## R7 — cambiar formato aleatorio no transforma la salida existente

La sonda genera dos bytes Hex, cambia únicamente el selector a Base64 y confirma `RANDOM_FORMAT_CHANGE_RENDERS=false`: la salida continúa idéntica. El listener solo sincroniza la barra; `handleGenerateRandom` genera bytes nuevos en cada ejecución. La descripción ES `help.action.op_gen_random.select_format.description` anuncia «Cambia cómo se muestran los mismos bytes generados» y la referencia afirma «Cambiar formato solo cambia la representación».

Corregir ES/EN y referencia: el selector determina la representación de la siguiente generación; por sí solo no reformatea la salida anterior, y volver a generar produce nuevos bytes. No modificar la funcionalidad para hacer cierta la explicación. Una comprobación focalizada debe preservar la salida al cambiar selector y contrastar el texto con ese contrato.

## R8 — cerrar el detalle y la evidencia de cobertura

Las opciones Check Digits siguen repitiendo «aplica su regla decimal de control» para los cuatro algoritmos. La referencia solo dice que Verhoeff y Damm tienen reglas distintas. C013 exige explicar qué significa cada operación y sus modos: incluir diferencias relevantes de las cuatro reglas, relación AMEX SE/Luhn según el consumidor, base frente a número completo, longitud mínima y ceros iniciales. No se exige un tratado matemático ni cambiar el cálculo. Contrastar fixtures pertinentes de las reglas no Luhn; la prueba UI actual solo usa el algoritmo predeterminado Luhn.

El addendum/matriz contiene grupos de fichas y una descripción general de enlaces, pero falta la matriz requerida por acción/campo/opción con control o fuente, ES/EN, regla, ejemplo, evidencia y limitación. Completar al menos B05.1 para comprobar que cada control y opción queda cubierto; reutilizar grupos/base y diferencias cuando resulte legible, sin ocultar pendientes tras un resumen de familia.

## Evidencia aceptada y límites

Se inspeccionaron los nuevos XML y argumentos de `target/b05-review/b051/`: 15 unidades correctas (6 catálogo B05, 7 registro, 2 CompressedHexCodec), ejecución 16:47:00–16:47:16 UTC; un método UI correcto con cuatro combinaciones ES/EN y 1800/900, ejecución 16:45:48–16:46:15 UTC. No se suman las pruebas de la subentrega anterior. El UI usa consumidores reales y cubre vectores/errores de las ocho fichas y algunas interacciones de ayuda; los casos R6/R7 no se verifican con su secuencia actual.

Sonda del revisor: `target/b05-review/reviewer/B051ReviewProbe.java`, log `b051-probe.log`, salida 0; confirma R6/R7. La sonda no se cuenta como JUnit y no sustituye el recorrido ES/EN/compacto de regresión. No se repiten suites sin causa. `git diff --check` correcto. No hubo instalación nueva durante esta revisión; se conserva la petición humana para un punto estable revisado.

Corrección siguiente: R6–R8, pruebas focalizadas y evidencia/documentación actualizada. Las partes correctas se conservan; no volver a implementar B05.1 desde cero. B05 completo solo puede aprobarse tras B05.2, B05.3 y cierre transversal.
