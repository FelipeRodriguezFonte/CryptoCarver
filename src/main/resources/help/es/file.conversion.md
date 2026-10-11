# Conversión de archivos

Formatos de origen/destino: Binary lee bytes; Text usa UTF-8, ASCII o ISO-8859-1; Hex lee texto, elimina espacios y exige pares de dígitos válidos; Base64 usa el decoder básico. Ejemplo: archivo Text `AB` a Hex produce `4142`. Destino opcional muestra hasta 1000 caracteres de salida en pantalla; al escribir, el destino debe ser una ruta válida y distinta del origen si quiere conservarlo.

Comparar lee origen y comparación como bytes sin atender a los selectores e informa igualdad o primer offset distinto (base cero); exige ambos archivos. Hash File calcula SHA-256 por bloques y comunica el tamaño. Preview muestra hasta 4096 bytes Hex del origen. Estas acciones requieren archivo origen legible; comparar requiere además comparación. Las rutas y el contenido no se publican en el contexto de ayuda. La conversión de archivos usa actualmente lectura completa; comparación y hash sí procesan por bloques.

Implementación: `FileConversionCoordinator`, `FileConverter`, `StreamingFileTools` y `generic.fxml`.
