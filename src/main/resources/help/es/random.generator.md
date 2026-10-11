# Bytes aleatorios

Introduzca un entero entre 1 y 1024 bytes inclusive. Se generan bytes con `SecureRandom`; la salida cambia en cada ejecución. Hexadecimal ocupa 2N caracteres; Binary, 8N bits separados por espacios; Decimal muestra N valores sin signo 000–255; Base64 usa grupos estándar con longitud múltiplo de cuatro. Ejemplo estructural: 16 bytes producen 32 caracteres Hex y al decodificar la salida se recuperan 16 bytes. Cero, 1025, decimales o texto no entero se rechazan y conservan la salida previa.

El selector fija el formato de la próxima generación. Cambiarlo no transforma la salida que ya está visible; al pulsar Generar se obtiene otra secuencia aleatoria en el formato elegido. No es un UUID ni una promesa de que los bytes sean adecuados como clave sin analizar su uso. Implementación: `GenericController.handleGenerateRandom`, `OperationFormatRegistry` y `generic.fxml`.
