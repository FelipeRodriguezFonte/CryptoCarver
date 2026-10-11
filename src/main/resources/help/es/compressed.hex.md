# Hex comprimido en dos filas

La pantalla tiene un único campo de entrada compartido por Expandir y Comprimir. Expandir lee exactamente dos filas no vacías: cada columna une el nibble de la fila alta con el de la fila baja. Deben tener igual longitud y contener solo Hex. Ejemplo: `1234` / `ABCD` → `1A2B3C4D`. Una sola fila, longitudes distintas o un carácter inválido se rechazan.

Comprimir recibe Hex convencional con cantidad par de dígitos e intercala posiciones pares/impares en las filas. `1A2B3C4D` → `1234` / `ABCD`. Se ignoran espacios y saltos de línea; no se acepta prefijo `0x`. El botón `?` de cada dirección establece la acción, y el `?` del campo describe el parser de esa dirección. «Comprimido» designa la disposición host, no compresión ni cifrado. Implementación: `CompressedHexCodec`, `CompressedHexController` y `compressed_hex.fxml`.
