# Revisión B05.2 — primera entrega

Fecha: 2026-10-10. Turno: `01a126cf-30a2-7c71-8266-1cf0c53906b2`.

**Dictamen PARCIAL. B05.2 no aprobado. B05.1 conserva su aprobación; B05.3 y B06 esperan.** Se contrastaron encargo, inventario, matriz, catálogo, recursos, conexiones y pruebas. No hubo instalación nueva ni cambio de la versión estable por el revisor.

La entrega añade claves para 40 operaciones Batch, 194 tipos de nodo, 543 parámetros y 747 opciones, y mejora la explicación de límites/CSV/JSONL y condiciones. Eso acredita inventario y conexiones iniciales, pero no completa semántica, ejemplos ni todos los controles. Mantener incompleto Process Designer mientras haya deuda; no equiparar número de claves con cobertura editorial.

## R9 — fichas dinámicas generadas sin diferencias funcionales

Los ejemplos de nodo son la misma instrucción general precedida del tipo y descripción: por ejemplo KDF_HKDF, JWS_SIGN y KEY_SPLIT_XOR terminan en «conecta un productor cuya salida coincida con sus puertos de entrada, configura sus parámetros y revisa el resultado de este nodo». No contienen configuración ni entrada/salida concreta del caso. La unicidad de strings pasa porque el nombre cambia; no prueba que el ejemplo sea específico.

Las opciones CRYPTO `AES/CBC/PKCS7Padding`, `AES/CTR/NoPadding` y `AES/GCM/NoPadding` dicen únicamente «Configura Algoritmo en Cifrar con …; el controlador del nodo utiliza exactamente este valor del catálogo». Faltan diferencias de padding, IV/nonce, autenticación y parámetros efectivos. `crypto.key` solo promete no leer secretos; `crypto.nonce` solo dice bytes Hex, sin tamaños/aplicabilidad por variante. `kdf_hkdf.outputLength` solo anuncia número y default 32, sin límites o significado del material producido. No se pide repetir manuales enteros: usar base de familia más diferencias verificables, con fuentes/consumidores y limitaciones.

Completar todas las familias de nodos, reglas por parámetro/opción, ejemplos concretos y errores pertinentes. No ejecutar claves/derivaciones para construir la ayuda. Los contratos de puertos calculados con defaults no sustituyen condiciones por opciones distintas; declararlas y probarlas cuando cambie el contrato.

Batch ha mejorado las descripciones por operación, pero un único ejemplo global Hex→UTF-8 no entrega los ejemplos/errores propios requeridos para cada entrada dinámica. Reutilizar base con casos de cada transformación o grupos equivalentes con diferencias; la matriz debe identificar la correspondencia y evidencia para las 40 entradas. Las pruebas de cálculo existentes son evidencia de consumidores, no ejemplos visibles de ayuda por sí solas.

## R10 — controles y acciones Process Designer sin enlace completo

`configureContextualHelp` enlaza seis controles generales y los parámetros renderizados. `process_designer.fxml` también expone Dry Run, Open, Save, Undo/Redo, Duplicate, Tidy layout, Clear, Reset, presets, Reverse connection, inspector/focus, resultados ampliados y ventana independiente. Parte de esas acciones consta en el catálogo, pero faltan enlaces propios desde sus controles y faltan otras acciones en el inventario semántico. El campo de nombre y otros controles reales tampoco quedan inventariados de forma auditable.

Completar mecanismos accesibles para consultar la ayuda contextual de controles y menús reales, con acción correcta, teclado y ES/EN. No basta que una acción sea seleccionable en una lista remota del inspector ni que el lienzo tenga un `?` de crear nodo. Conservar selección, conexiones, resultado y trabajo activo. Prueba desde la carga real, incluyendo menú, nodos menos comunes, consulta de parámetros sensibles, variantes, navegación y cierre/reapertura; no agregar formularios ni ejecutar la acción por consultarla.

## R11 — matriz y pruebas no certifican el alcance declarado

La sección B05.2 de la matriz contiene dos filas de familias. No permite cotejar todas las operaciones/nodos/acciones/campos/opciones y sus fuentes, textos, ejemplos, pruebas y pendientes. Añadir inventario granular o agrupado por base más diferencias que conserve cada ID obligatorio y su estado. Los tests comprueban longitud, claves distintas, textos distintos por nombre y ausencia de ciertas frases; pueden aceptar exactamente los textos genéricos de R9. Reforzar contratos editoriales verificables sin escribir tests que solo reflejen el generador; la revisión de contenido sigue siendo necesaria.

Los XML entregados corresponden a 112 unidades y 9 UI sin fallos/errores/omisiones: catálogo 9, Batch 33, ProcessEngine 60 y ProcessValidator 10; UI un método B05 contextual y ocho caracterizaciones. Metadatos registran ejecuciones finales 17:30:17–17:30:30 y 17:30:36–17:30:53 UTC, con intentos previos separados y reportes efectivos. La prueba contextual recorre ES/EN 1800/900, Batch y cambios entre nodos del flujo inicial; no recorre los 194 nodos ni todas las acciones/variantes. No se repiten suites: sus resultados válidos se conservan, y se requieren comprobaciones focalizadas de los cambios pendientes. `git diff --check` correcto.

## Continuación sin reducir alcance

Primero cerrar C009 Batch como B05.2a y corregir clasificación global de C010 a parcial. Después C010 en entregas de familias de nodos y acciones generales, manteniendo un inventario de pendientes hasta cubrir los 194 tipos y sus opciones. La infraestructura generada se conserva como borrador, sin contar fallback o paráfrasis de etiquetas como ayuda completa. B05.2 completo requiere cerrar R9–R11; B05.3 y B06 no se adelantan. La instalación estable de B05.1 se conserva.
