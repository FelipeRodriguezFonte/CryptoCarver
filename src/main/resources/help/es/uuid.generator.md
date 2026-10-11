# UUID v4

Generar crea una cadena canónica en minúsculas con 36 caracteres y grupos 8-4-4-4-12. Los 32 dígitos Hex codifican 128 bits, incluidos los bits reservados de versión/variante; el tercer grupo comienza con `4` y el cuarto con `8`, `9`, `a` o `b`. Copiar transfiere el valor visible al portapapeles y no genera uno nuevo.

Compruebe el formato con `UUID.fromString`, la versión 4 y la variante RFC 4122/9562 del valor resultante. UUID es un identificador aleatorio, no una clave secreta con 128 bits de entropía. En el contexto solo se comparten acción/campo; no se copia el valor. Implementación: `UUIDGenerator`, `GenericController` y `generic.fxml`.
