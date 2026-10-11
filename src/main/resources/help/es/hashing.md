# Hash y CRC32

La huella se calcula sobre los bytes, no sobre la apariencia del texto. En Hashing, la barra global admite Texto UTF-8, Hexadecimal, Base64 y Binary como entrada; la salida admite Hexadecimal, Base64 o Binary. Ejemplo positivo: SHA-256 de `abc` en UTF-8 es `BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD` (32 bytes). Hex impar o con caracteres ajenos a `[0-9A-F]` es inválido.

SHA-224/256/384/512 y SHA3-256/512 son familias distintas; MD4, MD5 y SHA-1 son heredados y no se recomiendan para resistencia a colisiones. WHIRLPOOL y TIGER-192 devuelven 64 y 24 bytes. CRC32/ISO-HDLC y las variantes ISO-HDLC, BZIP2, MPEG-2, POSIX, JAMCRC e iSCSI devuelven 4 bytes, pero solo detectan cambios accidentales; no autentican. Cambiar el formato de entrada puede cambiar los bytes y, por tanto, la huella.

Aplicar una plantilla ajusta valores no secretos y no ejecuta el hash. Los botones de administración guardan/seleccionan preferencias; la ayuda no incorpora nombres personales ni entrada/salida. Implementación: `HashingCoordinator`, `HashOperations`, `OperationFormatRegistry` y `generic.fxml`.
