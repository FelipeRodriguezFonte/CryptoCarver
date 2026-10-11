# Revisión B05.2b1 — C010 controles y 36 nodos

Fecha: 2026-10-10, 20:43 Europe/Madrid. Turno revisado: `01a126fc-d07b-76e1-b458-40e8fed39a5c`.

**NO APROBADO todavía. Se conserva el avance; B05.2b1 requiere R12–R15. C010 sigue parcial, los otros 158 nodos y B05.3/B06 esperan.**

Se contrastaron encargo activo, encargo integral, inventario, matriz, catálogo, controller, renderer, recursos efectivos ES/EN y pruebas. Hay botones generales y de paleta, contexto público y limpieza de listeners. Los informes nuevos acreditan cinco pruebas unidad/catálogo/FXML y una UI sin fallos/errores/omisiones, 18:36:49 y 18:40:05 UTC. Se comprobaron métodos y XML; no se repitió Maven. `git diff --check` correcto. Su alcance no demuestra todos los criterios editoriales o de presentación que la matriz atribuye al corte.

## R12 — Opciones y parámetros todavía genéricos

`messages_es.properties:6575–6580` conserva las seis opciones PAD como “Configura Tipo de relleno … el controlador … utiliza exactamente este valor”. UNPAD conserva el mismo patrón (`:6853` para ZERO). `help.node.parameter.unpad.paddingtype` y SLICE offset/length siguen siendo metadatos sin explicar contrato: base cero, -1, límites o pérdida de ceros al desrellenar. No explican diferencias PKCS5/PKCS7/ISO_7816_4/ISO_9797_M1/M2/ZERO. `B05HelpCatalog.nodeOptionDescriptionKey` usa exactamente esas claves, sin reemplazo semántico. El test focal solo exige presencia, por eso pasa.

Corregir todo el corte de 30 parámetros y 146 opciones frente al consumidor real; base por familia más diferencias explícitas es válida. No contar etiquetas/defaults o paráfrasis como explicación. Registrar filas auditables por control/nodo/parámetro/opción, fuente, regla y caso comprobado; la tabla actual agrupa categorías y totales.

## R13 — Ejemplos negativos y ES incompletos

El ejemplo visible CHARSET_CONVERT (`messages_es.properties:8019`) presenta US-ASCII incapaz de representar é como caso negativo, pero el handler acepta y sustituye. La clave adicional `.charset_convert.error` (`:7904`) afirma en inglés que falla sin sustitución, contradiciendo la propia nota de la matriz. Sonda independiente con grafo CONSOLE_INPUT ISO-8859-1 → CHARSET_CONVERT US-ASCII: **SUCCESS render=? bytes=3f**, salida 0 (`target/b05-review/reviewer/b052b1/charset-probe.log`). No cambiar motor: explicar sustitución como limitación, y usar un error real para el ejemplo negativo.

También quedan errores en inglés/mezcla en el bundle español, por ejemplo `.node.slice.error`, `.node.pad.error`, `.node.xor.error` (:7934). Esas claves `.error` no se conectan al ActionHelp/render actual; el ejemplo combinado sí aparece. Traducir o retirar duplicados obsoletos y decidir una sola fuente coherente visible. Comprobar positivos/negativos contra consumidor: la prueba `B052B1ReviewedNodeConsumersTest` contiene 35 vectores positivos y solo el error heredado BASE64, no los negativos que la matriz atribuye a los 36 nodos.

## R14 — Borrar selección sin ayuda

`ProcessDesignerController` declara `deleteSelectedButton` (:50), y `handleDeleteSelected` (:809) distingue borrar conexión y nodo(s). `configureContextualHelp` enlaza clearCanvas pero no deleteSelectedButton. Tampoco hay acción delete en SPECS del diseñador. Falta explicar esta operación efectiva, sus dos estados, conexiones incidentes y alcance de deshacer según comportamiento actual. Añadir ayuda directa accesible y revisar los demás controles efectivos contra inventario, incluidos menús/contextos/atajos. Clear canvas no sustituye borrar selección.

## R15 — Regresión sobre textos visibles y teclado

La UI nueva comprueba IDs/valores de contexto y existencia de botones; no lee el texto del inspector ni de la ventana compacta, y usa `fire()` sin eventos de teclado. Tampoco cambia idioma manteniendo una selección: crea un shell por idioma. Así no acredita que parámetros/opciones, ejemplos, reglas y negativos se expongan correctamente ni la preservación al cambiar ES/EN.

Añadir pruebas pertinentes sobre contenido realmente renderizado en ambas vistas y anchuras: nodo menos común de cada familia, parámetro y opción PAD→ZERO, navegación/reapertura, cambio de idioma del mismo shell, activación por teclado y modo apagado; conservar entradas/resultados/grafo/conexiones y no ejecutar por consultar. Comprobar los errores documentados y conexiones incompatibles/puertos ausentes del corte. No repetir suites amplias del motor sin causa.

## Continuación

Corrección R12–R15 en el mismo chat/modelo. La clave Payments ausente `module.process.param.payment.dsInput` permanece deuda documentada fuera del corte; no usar suite amplia fallida como evidencia correcta ni perderla en el cierre C010/B15. No instalar esta entrega todavía: última versión revisada instalada es B05.2a. Instalar el siguiente punto estable tras aprobación, conservando copias/prefs y sin cerrar trabajo abierto del usuario.
