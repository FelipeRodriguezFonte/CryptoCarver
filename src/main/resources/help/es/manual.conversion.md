# Conversión manual

La barra local ofrece Texto UTF-8, Hexadecimal, Base64, Base64URL, Base94, Binary y Decimal. Convertir interpreta la entrada con ese selector y representa los mismos bytes con el formato de salida. Ejemplo: Hex `4142` a Base64 produce `QUI=`. Hexadecimal debe tener dígitos pares.

Las acciones especiales tienen parsers propios: Base64URL codifica texto UTF-8 sin padding y rechaza símbolos ajenos a su alfabeto; Base32 codifica texto UTF-8 con padding (`A` → `IE======`); URL usa escapes de formulario UTF-8 (`a b` → `a+b`, y `+` decodifica como espacio). Endian invierte bytes por palabra de 16/32/64/128 bits: `01020304` con 32 bits → `04030201`; longitud no divisible por el tamaño es inválida.

EBCDIC se activa con la casilla de conversión y usa dirección/página seleccionadas; UTF-8 `AB` a IBM037, salida Hex, produce `C1C2`. Compresión usa gzip/zlib/deflate sobre bytes del formato local; descompresión debe seleccionar el envoltorio correspondiente y está limitada a 64 MiB. No cifra. BCD empaqueta dígitos decimales de dos en dos (impar antepone cero); COMP-3 admite entero decimal con signo C/D al final. Shift interpreta Hex y conserva ancho; solo acepta cantidad de bits entera no negativa. Extract Trace Hex busca corridas de al menos 4 bytes y omite grupos cortos/offsets convencionales.

Aplicar/administrar plantillas no ejecuta la conversión. Consulte el botón activo: no todas las acciones usan los formatos de la barra. Implementación: `ManualConversionCoordinator`, `SafeTransformations`, `DataConverter` y `generic.fxml`.
