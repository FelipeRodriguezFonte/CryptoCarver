# Revisión B05 — subentrega 01

Fecha: 2026-10-10. Turno revisado: `01a12684-2a17-7c70-876a-fba464a16b6c`.

**Dictamen: PARCIAL, requiere continuación y correcciones. B05 no está aprobado; B06 permanece pendiente.** La entrega añade catálogo, referencias y conexiones iniciales. El alcance obligatorio continúa siendo C006–C017, A004/A005/A006/A011 y todas las entradas seleccionables de BatchOperationCatalog y NodeCatalog.

Se contrastaron el encargo, inventario y matriz, el catálogo B05, las conexiones Generic/Compressed Hex/Process Designer/auxiliares, los bundles propios y las pruebas entregadas. Esta revisión de subentrega no certifica las pantallas restantes ni sustituye el recorrido integral requerido para cerrar el bloque.

## R1 — estado completo prematuro

`B05HelpCatalog.build` devuelve `complete=true` para las 16 fichas aunque la matriz reconoce pendientes de contenido, controles y opciones. `B05HelpCatalogTest` exige ese valor. El renderer solo informa de detalle pendiente cuando `complete=false`; por tanto la clasificación afecta a la presentación, no solo al informe.

Corregir la clasificación de las fichas parciales y su prueba. Promover cada ficha únicamente después de cotejar acciones, campos, opciones, traducciones y evidencia; conservar B05 abierto hasta cerrar todo su alcance.

## R2 — ES propio no equivale a ES traducido

Los bundles propios evitan fallback, pero contienen textos ingleses: por ejemplo `help.field.op_gen_hash.input.description`, las acciones de Byte Inspector, `help.action.aux_epoch.convert.*`, `help.node.parameter.sensitive` y `help.operation.op_gen_process_designer.purpose`. Otros textos mezclan idiomas o agregan una frase española a un ejemplo inglés. Comprobar existencia de claves no detecta esto.

Entregar contenido completo ES/EN, incluidos ejemplos, etiquetas, acciones y condiciones. La revisión editorial debe mirar el texto presentado; permitir nombres técnicos o códigos de catálogo no justifica párrafos sin traducir.

## R3 — semántica y opciones todavía genéricas o discordantes

La descripción española de `op_gen_manual.endian` se limita a «Aplica la acción endian…» y su ejemplo corresponde a Hex/Base64, no al orden por palabras. Random omite límites concretos. Las entradas Batch repiten «parsing y salida siguen esta entrada del catálogo» y las opciones Node reutilizan la ayuda del parámetro o un texto genérico, sin explicar sus diferencias. `visibleWhen` se muestra como expresión interna en lugar de una condición legible. Esto no constituye cobertura completa.

Hay además una discordancia verificable en A005: la ficha anuncia unidad seleccionada, segundos o milisegundos y zona seleccionada. `UtilityToolsCoordinator.handleEpochConverter` ofrece un único timestamp en segundos y una salida UTC de solo lectura; `EpochTimestampConverter.toUtc` aplica `Long.parseLong` e `Instant.ofEpochSecond`. No hay selector de unidad/zona ni conversión inversa en ese diálogo. Documentar la capacidad efectiva y su limitación, sin añadir funciones de conversión para hacer coincidir la ficha. Aplicar el mismo cotejo a JSON, Workbench, Envelope y los demás casos pendientes.

Cada acción necesita significado, parser/formatos efectivos, condiciones, unidades, valores permitidos, ejemplo específico y caso negativo. Las opciones dinámicas requieren ayuda propia o una base con diferencias reales, no una frase que delega la explicación al catálogo.

## R4 — enlace y contexto por control todavía incompletos

Compressed Hex usa el mismo TextArea como entrada de ambas direcciones, pero su `?` permanece ligado a `expand/high_row`; no describe la entrada Hex convencional de `compress`. La ficha enumera filas y separador que no son controles separados. Ajustar el modelo a la entrada combinada real y conservar contexto por acción/dirección, sin crear campos ficticios. Verificar ambas direcciones y consulta de entrada/salida tras navegar y volver.

Completar el inventario de controles reales y sus enlaces para cada pantalla y ventana Java; la matriz reconoce pendientes. En Process Designer deben verificarse todas las selecciones de nodo, parámetros sensibles, condiciones y puertos, además de la prioridad inicial del nodo. Preservar entradas, resultados, conexiones y estado de trabajos. La consulta no debe ejecutar acciones ni publicar valores libres o secretos en el contexto.

## R5 — cobertura de evidencia

Se inspeccionaron XML, propiedades y código de pruebas: 41 pruebas de unidad/contrato nuevas (B05HelpCatalogTest: 3; OperationHelpRegistryTest: 7; FxmlContractTest: 2; ModernMainControllerFxmlStaticTest: 29), copiadas a las 16:14:44 UTC, y una prueba UI nueva a las 16:15:07 UTC. Todas registran cero fallos, errores u omisiones y argumentos concordantes; no se mezclan informes anteriores. La prueba UI es un método que recorre ES/EN y 1800/900, no cuatro casos JUnit distintos.

Su evidencia acredita presencia de claves/listas/referencias y una interacción Hashing más selección inicial de Process Designer. No acredita reglas reales, todas las operaciones, ventanas, opciones, teclado ni lifecycle completo. El nombre de la prueba sobre secretos de nodos comprueba metadatos del catálogo, no ausencia de valores sensibles en una interacción real. No se repiten estas suites: para la siguiente entrega se requieren pruebas pertinentes de los cambios y consumidores efectivos, con informes nuevos, preferencias aisladas y ejecuciones Maven secuenciales. `git diff --check` del revisor correcto.

## Secuencia de continuación

Para mantener entregas revisables se divide el resto sin reducir alcance:

1. B05.1: C006/C007/C008/C011/C012/C013/C014 y A011. Cerrar sus acciones, campos, opciones, ES/EN, contextos y fixtures. Corregir R1 global al empezar; no esperar a que cierre todo B05.
2. B05.2: C009/C010 y todas las entradas dinámicas de lote/nodos, condiciones y parámetros.
3. B05.3: C015/C016/C017 y A004/A005/A006, controles y ventanas reales.
4. Cierre B05: regresiones compartidas pertinentes, matriz control por control y dictamen de cobertura completa.

Luna entrega B05.1 y espera revisión; Codex despacha la siguiente subentrega. B06 requiere aprobación del bloque completo. Se mantiene la petición humana de instalar versiones revisadas localmente, con copia recuperable y preservación de datos, en un punto estable antes de continuar al bloque siguiente. La comprobación integral del empaquetado fuera del checkout sigue siendo obligatoria en B15.
