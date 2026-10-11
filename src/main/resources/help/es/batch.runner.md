# Batch Runner — C009 / B05.2a

El panel aplica una de las 40 entradas visibles de `BatchOperationCatalog` al campo origen configurado de cada fila. Devuelve un informe por fila; no es la rama de cifrado de registros. El selector no ofrece Encrypt Record ni Decrypt Record. Sus controles de algoritmo, clave, IV/nonce, AAD, representación, charset y compactación permanecen ocultos y no afectan a las operaciones seleccionables.

## Contrato de entrada y filas

El selector de formato interpreta el texto pegado. CSV requiere una cabecera única, campos separados por comas, celdas entre comillas dobles, comillas duplicadas dentro de celdas entrecomilladas y el mismo número de columnas en cada fila. Las celdas entrecomilladas pueden contener comas y saltos de línea. JSON Lines acepta un objeto JSON no vacío por línea; sus valores deben ser escalares, por lo que rechaza arrays y objetos anidados. Un array o documento JSON completo no es JSONL.

Ambos parsers rechazan cabeceras o nombres vacíos/duplicados y nombres de campo sensibles como `key`, `password`, `pin`, `pan`, `cvv`, `iv`, `nonce`, `aad`, `salt`, `secret`, `private`, `certificate`, `token`, `signature` o `mac`. Cada celda admite como máximo 100.000 caracteres; la entrada total, 100.000.000; el parser, 10.000 filas. JSONL además limita cada línea a 5.000.000 caracteres. Se omiten registros CSV y líneas JSONL vacíos.

El nombre de columna origen no puede estar vacío y debe aparecer en todas las filas. La operación seleccionada recibe ese valor como texto. Una fila correcta añade la salida con el nombre de columna destino. Un error de parseo o de columna ausente bloquea el lote; los errores de operación se conservan en sus filas. Si Detener ante error está activo, el primer fallo de un worker solicita cancelación cooperativa y pueden terminar otras filas ya en ejecución; si está desactivado, se procesan las restantes. El panel muestra hasta 50 resultados. El progreso cuenta las filas terminadas, también las erróneas.

## Acciones y estado

- **Cargar entrada** lee el fichero elegido como UTF-8. El diálogo acepta CSV, JSONL, NDJSON o TXT; `.csv` selecciona CSV y las demás extensiones seleccionan JSON Lines. El texto pegado se interpreta con el selector visible.
- **Simular** parsea las filas y valida columnas y disponibilidad de la operación. No invoca la operación, no escribe un fichero ni crea historial. La vista previa reemplaza el texto de resultados, pero conserva la entrada.
- **Ejecutar** inicia el procesamiento en segundo plano y limpia el informe visible anterior. Un informe terminado se puede exportar.
- **Cancelar** solicita la cancelación. El coordinador descarta el informe cancelado y limpia resultados visibles; los resultados parciales no se exportan.
- **Reiniciar** cancela el trabajo activo, vacía entrada/resultados, reinicia progreso y estado del informe, y borra controles criptográficos temporales.
- **Exportar** escribe el informe completado como CSV o JSON Lines UTF-8. El formato de salida es independiente del de entrada. CSV escapa celdas entrecomilladas; JSON Lines crea un objeto resultado por fila.
- **Operación, entrada, resultado, progreso y estado** tienen ayuda contextual directa. La selección de operación solo añade su código público al contexto de ayuda; la ayuda no inspecciona entrada, resultado, ruta ni secretos.

## Ayuda por operación

Cada entrada seleccionable tiene contrato, ejemplo concreto y caso negativo propios en la ayuda del selector, localizados. Se muestran para la operación seleccionada al consultar el selector, la columna origen/resultado o una acción. Los textos siguen `BatchOperationCatalog.execute` y los codecs/consumidores vigentes: hashes/checksums procesan texto UTF-8, las conversiones decodifican solo la representación indicada y los analizadores estructurales devuelven JSON sin afirmar que validan un protocolo completo. La matriz relaciona cada etiqueta live con su clave de opción y vector probado.
