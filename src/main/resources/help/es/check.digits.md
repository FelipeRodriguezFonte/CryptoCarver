# Dígitos de control

Calcular recibe la base sin dígito final (mínimo una cifra) y muestra el dígito más el número completo. Validar recibe el número completo (mínimo dos cifras: base y control) y comprueba su última cifra. Ambos exigen dígitos ASCII `0`–`9`; letras, espacios y separadores se rechazan. El valor se mantiene como texto, así que se conservan ceros iniciales: `00123` no se convierte al entero `123`.

Reglas que expone el selector:

- **Luhn (Mod 10):** duplica cifras alternas de la base empezando por la derecha, resta 9 a cada resultado superior a 9 y calcula la cifra que completa un múltiplo de 10. `7992739871` → `79927398713`.
- **AMEX SE (Luhn/Mod 10):** en esta aplicación es una etiqueta que usa exactamente el mismo cálculo Luhn; no comprueba emisor, rango de tarjeta ni validez específica de AMEX. La base `572` produce `5728`.
- **Verhoeff:** usa tablas de multiplicación, permutación por posición e inversa; la implementación detecta cualquier error de una cifra y la mayoría de transposiciones. La base `572` produce `5727`.
- **Damm:** pliega las cifras de izquierda a derecha con una tabla de cuasigrupo no conmutativa; la implementación detecta cualquier error de una cifra y cualquier transposición adyacente. La base `572` produce `5724`.

El dígito ayuda a detectar ciertos errores de transcripción. No acredita identidad, emisor ni autenticidad. Referencia del código: `CheckDigitCoordinator`, `CheckDigitCalculator` y `generic.fxml`; no se valida ninguna regla externa del sistema de numeración.
