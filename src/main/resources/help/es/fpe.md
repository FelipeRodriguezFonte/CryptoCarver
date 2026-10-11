# Cifrado que conserva el formato

El panel FPE de CryptoCarver aplica FF1 o FF3-1 a una cadena definida sobre un alfabeto ordenado. Conserva la longitud y el alfabeto de la cadena, pero no autentica el resultado ni oculta el alfabeto. La clave AES se introduce en Hex y debe tener 16, 24 o 32 bytes. El tweak es opcional en FF1 y debe tener exactamente siete bytes en FF3-1; para descifrar se usan el mismo tweak y el mismo orden exacto del alfabeto.

Los alfabetos incluidos son Decimal (radix 10), Alfanumérico (62 símbolos: dígitos, mayúsculas y minúsculas) y ASCII imprimible (95 símbolos). El alfabeto personalizado admite entre 2 y 256 símbolos únicos de un byte. Cada símbolo de entrada debe pertenecer al alfabeto y el dominio debe contener al menos 1.000.000 valores. El límite actual de entrada es 4096 símbolos. FF1 admite un tweak opcional de hasta 1024 bytes. FF3-1 exige exactamente 7 bytes de tweak y tiene un límite de entrada más estricto, dependiente del radix y derivado de su dominio de 96 bits.

Para comprobar la reversibilidad, cifra una entrada decimal válida de prueba con FF1 y una clave AES de prueba; después descifra con la misma clave, tweak y alfabeto. Se rechazan símbolos personalizados repetidos, entradas ajenas al alfabeto y tweaks FF3-1 de seis bytes. El resultado no está autenticado hasta validarlo por separado.
