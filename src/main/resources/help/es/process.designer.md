# Catálogo de nodos Process Designer

La paleta y el inspector parten de todos los descriptores actuales de `NodeCatalog`. Cada nodo seleccionado tiene una acción de ayuda propia, un ejemplo específico, ayuda para sus parámetros y un contrato de puertos. La ayuda de puertos enumera cada entrada, las representaciones que acepta, si es obligatoria y la representación de salida producida con los valores iniciales del descriptor. El nodo seleccionado prevalece sobre la ruta general de Process Designer; al cambiar la selección se sustituye la acción y el contexto del parámetro.

## Parámetros, condiciones y secretos

La ayuda de cada parámetro se genera desde el descriptor activo e identifica el tipo de control, la etiqueta y el valor inicial del catálogo, si existe. Las opciones de combo se documentan por nodo y parámetro. Los parámetros condicionales indican qué opción de control hace que aparezcan. El handler sigue determinando los requisitos de validación y ejecución; un campo de texto vacío no se declara obligatorio salvo que el handler activo aplique esa regla.

Los parámetros sensibles nunca se leen para construir la ayuda. El contexto conserva solo el tipo estable de nodo, el ID de parámetro y códigos de selección públicos; excluye valores escritos, secretos, payloads de proceso, resultados y rutas de archivos. Los valores sensibles transitorios no se guardan en los ficheros de proceso. Al reabrir uno puede ser necesario volver a introducirlos.

## Validar, simular y ejecutar

Las conexiones deben usar representaciones aceptadas por el puerto de destino; los puertos obligatorios deben estar conectados y los nodos con varias entradas requieren el puerto correcto. El preflight informa de parámetros incompletos, conexiones incompatibles y entradas obligatorias ausentes antes de ejecutar. La simulación valida y muestra el plan sin invocar los handlers. Ejecutar procesa el grafo en orden de dependencias, informa del progreso y resultado por nodo y permite cancelar. Un flujo de conversión a hash es válido cuando la representación de salida de la conversión está admitida por la entrada de hash. Guardar/abrir serializa el grafo y la configuración que admite el codec activo; no incorpora valores secretos transitorios a la ayuda contextual.
