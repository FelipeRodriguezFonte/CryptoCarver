# Referencia JWE

JSON Web Encryption (JWE) cifra y autentica un contenido. El campo `alg` gestiona o deriva la clave de cifrado de contenido (CEK); `enc` cifra y autentica el contenido. Son elecciones independientes.

## Serialización y destinatarios

- Compact tiene cinco segmentos Base64URL, un destinatario y no admite el miembro `aad` externo.
- Flattened JSON representa un destinatario. General JSON usa una lista de destinatarios y permite varios cuando los algoritmos elegidos son compatibles.
- En esta aplicación el AAD externo es texto UTF-8. La serialización JSON lo lleva en el miembro `aad`; se autentica, pero no se cifra.
- En entrada General JSON con varios destinatarios, cada JWK aporta `alg` y sus valores por destinatario `kid`, `apu` y `apv`. `ECDH-ES` directo se limita a uno. `dir` proporciona directamente la clave de contenido.
- Con varios destinatarios en General JSON, el composer rechaza cualquier `kid`, `apu` o `apv` global no vacío; incluye esos valores en los JWK destinatarios. Cada JWK destinataria debe declarar `alg`.

## Opciones de gestión de claves

El transporte RSA necesita la clave pública al cifrar y la privada correspondiente al descifrar. `RSA1_5` está disponible por compatibilidad; las variantes OAEP usan el hash indicado por el identificador JWA. AES-KW y AES-GCMKW usan una clave compartida de envoltura. GCMKW tiene un IV y una etiqueta de envoltura propios, distintos del IV y la etiqueta del contenido.

ECDH-ES usa una clave destinataria EC u OKP. La variante directa deriva la CEK; las variantes `+AxxxKW` derivan una clave AES de envoltura. `apu` y `apv` son datos opcionales de participantes. PBES2-HS256+A128KW, PBES2-HS384+A192KW y PBES2-HS512+A256KW derivan una clave de envoltura desde una contraseña y usan las iteraciones configuradas. Si el campo está vacío, el valor predeterminado es 600000; si contiene un valor, debe convertirse en un entero de al menos 1000. La interfaz aplica esta validación a cualquier `alg` de cifrado, también si no es PBES2. Esto documenta la regla de entrada de la aplicación y no afirma una política universal. `dir` usa directamente el secreto simétrico como CEK, cuyo tamaño depende de `enc`.

## Cifrado de contenido y formatos del secreto

`A128GCM`, `A192GCM` y `A256GCM` usan CEK de 16, 24 y 32 bytes. `A128CBC-HS256`, `A192CBC-HS384` y `A256CBC-HS512` usan CEK de 32, 48 y 64 bytes. Los secretos/contraseñas pueden leerse como UTF-8, Hex o Base64/Base64URL. UTF-8 convierte directamente el texto; Hex ignora espacios y un prefijo `0x` opcional; Base64/Base64URL ignora espacios. El formato cambia los bytes interpretados, no el algoritmo. El selector de descifrado es local a esa acción e independiente del selector de cifrado.

Para cifrar, el material de clave destinataria admite PEM (incluidas las formas RSA/EC admitidas), certificado X.509, DER codificado en Base64, JWK o JWKS de una sola clave. El descifrado requiere la clave privada correspondiente en un formato PEM, DER Base64, JWK privado o JWKS de una sola clave admitido; un certificado X.509 solo aporta una clave pública. El cifrado General JSON con varios destinatarios requiere un JWKS con al menos dos claves destinatarias, cada una con su propio `alg`.

## Cabeceras y resultados

`typ` identifica el objeto JWE; `cty` describe su contenido (usa `JWT` si dentro hay un JWT). `kid` identifica la clave destinataria. `apu` y `apv` son valores ECDH opcionales. La lista exacta de nombres reservados para cabeceras personalizadas es `alg`, `enc`, `zip`, `epk`, `iv`, `tag`, `p2s` y `p2c`. Por ejemplo, `{"note":"demo"}` se acepta; `{"alg":"dir"}` y `{"p2c":600000}` se rechazan. El contenido y el material de descifrado son sensibles. La ayuda contextual solo recibe códigos de opciones permitidos e indicadores derivados de vacío/no vacío; nunca lee su texto. Abrir la ayuda de descifrado no analiza el JWE recibido ni infiere su `alg`, `enc` o serialización a partir del token o de resultados anteriores.

El desglose distingue cabecera protegida, clave cifrada, IV del contenido, ciphertext y etiqueta de autenticación del contenido. La CEK recuperada es material sensible; la ayuda no la obtiene ni muestra. En GCMKW, el IV y la etiqueta de envoltura son independientes de los del contenido.

## Ejemplos sintéticos y diagnósticos

Para un destinatario, cifra un mensaje sintético con una clave pública RSA de prueba usando `RSA-OAEP-256`, `A256GCM` y Compact; descífralo con la privada correspondiente. Para `dir`, elige `A256GCM` y un secreto de prueba de 32 bytes. Para varios destinatarios, elige General JSON y proporciona al menos dos JWK destinatarias compatibles que incluyan `alg`.

Casos negativos esperados: AAD no vacío con Compact se rechaza; un secreto `dir` cuya longitud decodificada no coincide con `enc` se rechaza; alterar la etiqueta de autenticación hace fallar la autenticación/descifrado. Esto describe el comportamiento actual de `JweComposer`; no promete compatibilidad más amplia.

## Referencias

- [RFC 7516: JSON Web Encryption (JWE)](https://www.rfc-editor.org/rfc/rfc7516.html)
- [RFC 7518: JSON Web Algorithms (JWA)](https://www.rfc-editor.org/rfc/rfc7518.html)
- [RFC 7519: JSON Web Token (JWT)](https://www.rfc-editor.org/rfc/rfc7519.html)
- Implementación: `com.cryptocarver.crypto.JweComposer`, `com.cryptocarver.ui.JoseJweCoordinator` y `src/main/resources/fxml/jose.fxml`.
