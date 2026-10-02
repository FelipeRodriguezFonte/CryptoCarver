# Caracterización previa de Keys, fase 2

Producción intacta en main / 6f86a9b. Carga real mediante Fxml.loader sobre main-view-modern.fxml y navegación perezosa; test.mode=true, user.home=target/test-home. Se comprueba finalización real (executor IDLE y snapshot publicado) sin sleep fijo.

Salida resumida de Surefire, Maven exit 0:

```text
ui.KeysSplit2CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=23.223
crypto.KeyDerivationTest: tests=4, failures=0, errors=0, skipped=0, time=0.003
crypto.KeyWrapOperationsTest: tests=3, failures=0, errors=0, skipped=0, time=0.002
ui.ComputedStyleSnapshotTool: tests=1, failures=0, errors=0, skipped=0, time=11.628
```

31 casos UI: RSA mínimo, DSA mínimo, EC por defecto y Ed25519; inspector, histórico y Shelf; los 10 KDF; KW/KWP y KEK incorrecta; firma/verificación CMS, cifrado/descifrado, certificado ligado a otra clave; cadenas válida, sin raíz y caducada; configuración portable/redacción e idioma para los cuatro bloques.

Vectores de KDF y wrap preexistentes se comparten desde KdfWrapTestVectors, sin cambiar valores ni aserciones de los dos tests crypto. HKDF SHA1/512 y SP 800-108/X9.63 usan oráculos independientes de HMAC/digest con entradas fijas; HKDF-SHA256 usa el vector RFC 5869 anterior. PBKDF2 emplea entradas password/salt/c=1 y salidas fijas (SHA1, RFC 6070; SHA256/512 mismas entradas). SCrypt usa RFC 7914 N=16384/r=8/p=1. Argon2id usa entrada fija password/somesalt, t=3, 64 MiB y cuatro lanes: su oráculo llama directamente a Argon2BytesGenerator y evita el wrapper KeyDerivation; no se presenta como vector externo independiente de BouncyCastle.

La pantalla CMS verifica con el certificado incluido, no con el campo de firma local: el caso negativo firma con una clave y otro certificado incrustado. La cadena se construye por PKIX, por lo que la ruptura se prueba omitiendo la raíz, no suponiendo que un cambio de orden deba fallar. Generación asimétrica no modifica Key Lab; los formatos ofertados son informes + PEM, sin selector DER/JWK en esta vista. Son caracterizaciones del comportamiento original, no cambios para satisfacer expectativas supuestas.
