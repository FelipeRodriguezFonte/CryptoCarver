# Tutoriales prácticos de CryptoCarver

Esta colección documenta la aplicación de escritorio y propone laboratorios reproducibles con entradas, parámetros, salidas, pruebas negativas y diagnóstico.

> CryptoCarver es un laboratorio educativo y de pruebas. No introduzcas claves, PIN, certificados ni datos de producción.

Las versiones maquetadas se generan dentro de [pdfs](pdfs/).

## Cómo orientarse

1. **Barra superior:** formato del payload y de la salida.
2. **Rail izquierdo:** 13 secciones principales.
3. **Explorador:** operaciones y ejecuciones recientes.
4. **Migas de pan:** sección, módulo y operación.
5. **Área central:** parámetros, entrada y salida.
6. **Inspector:** madurez, sensibilidad, tamaños y contexto.

El formato global se aplica al payload. Claves, IV, nonces, firmas y certificados tienen formatos propios.

## Rutas de aprendizaje

### Ruta simétrica

[Generar AES-256](05-claves.md) → [envolver claves con AES-KW/KWP](14-aes-wrap-avanzado.md) → [transportar claves y atributos con TR-31](15-tr31-avanzado.md) → [cifrar con AES-CBC/GCM](03-cifrado.md) → [calcular HMAC/CMAC](04-autenticacion.md) → [exportar receta segura](13-historial.md).

### Ruta asimétrica

[Generar RSA/ECDSA](05-claves.md) → [RSA-OAEP](03-cifrado.md) → [firmar y verificar](04-autenticacion.md) → [emitir certificado](08-certificados.md).

### Ruta de protocolos

[JWT/JWE](09-jose.md), [COSE](10-cose.md), [XAdES/WS-Security](07-seguridad-xml.md) y [CMS/PAdES](08-certificados.md).

### Ruta de pagos

[Generar y validar claves](05-claves.md) → [PIN/CVV/DUKPT/EMV](11-pagos.md) → [historial redactado](13-historial.md).

## Tutoriales por sección

| # | Sección | Tutorial |
|---|---|---|
| 1 | Búsqueda | [Localizar y encadenar operaciones](01-busqueda.md) |
| 2 | Genéricas | [Transformaciones y utilidades](02-operaciones-genericas.md) |
| 3 | Cifrado | [AES, 3DES y RSA-OAEP](03-cifrado.md) |
| 4 | Autenticación | [Firmas digitales, HMAC y CMAC](04-autenticacion.md) |
| 5 | Claves | [Generación, validación y uso](05-claves.md) |
| 6 | Post-cuántica | [KEM y firmas PQC](06-post-cuantica.md) |
| 7 | Seguridad XML | [XAdES, TSA y WS-Security](07-seguridad-xml.md) |
| 8 | Certificados | [CSR, cadenas, CMS, PAdES y ASiC](08-certificados.md) |
| 9 | JOSE | [JWT, JWS, JWE y JWK](09-jose.md) |
| 10 | COSE | [Sign1, Mac0 y Encrypt0](10-cose.md) |
| 11 | Pagos | [PIN, CVV, DUKPT y EMV](11-pagos.md) |
| 12 | ASN.1 | [Decodificar y construir DER](12-asn1.md) |
| 13 | Historial | [Recetas, sesiones y evidencia](13-historial.md) |
| 14 | AES Key Wrap | [RFC 3394, RFC 5649, vectores e integridad](14-aes-wrap-avanzado.md) |
| 15 | TR-31 / X9.143 | [Bloques de clave, cabeceras y atributos](15-tr31-avanzado.md) |
| 16 | CMS / PKCS#7 | [SignedData, EnvelopedData, CAdES e inspección ASN.1](16-cms-pkcs7.md) |
| 17 | Pagos avanzados | [PIN cifrado, PVV/offset, DUKPT TDES/AES y EMV](17-pagos-avanzado.md) |
| 18 | Firma y contenedores | [PAdES, ASiC-S, CSR y evidencia de revocación](18-pades-asic-s-y-revocacion.md) |
| 19 | TR-34 y PKCS#11 | [Distribución KDH/KRD, transporte RSA y perfiles de token](19-tr-34-pkcs11-y-transporte-rsa.md) |
| 20 | XAdES y WS-Security avanzados | [Firmas XML, validación de confianza, UsernameToken y SOAP firmado](20-xades-y-ws-security-avanzados.md) |
| 21 | Formatos, almacenes y PKCS#11 | [PEM, DER, PKCS#12, inventario y custodia en token](21-formatos-de-claves-almacenes-y-pkcs11.md) |
| 22 | Token PKCS#11 | [Inicializar SoftHSM, generar claves residentes y operar desde CryptoCarver](22-generar-un-token-pkcs11.md) |
| 23 | Herramientas | [Inspector de bytes y codificaciones](23-inspector-de-bytes.md) |
| 24 | Cifrado | [Análisis de un archivo cifrado](24-analizar-un-archivo-cifrado.md) |

## Tutoriales avanzados

| Tema | Tutorial |
|---|---|
| AES Key Wrap | [RFC 3394, RFC 5649, vectores oficiales e integridad](14-aes-wrap-avanzado.md) |
| TR-31 / X9.143 | [Versiones B y D, cabeceras, atributos, bloques opcionales e integridad](15-tr31-avanzado.md) |
| Pagos | [PIN cifrado, PVV/offset, DUKPT TDES/AES y EMV](17-pagos-avanzado.md) |
| PAdES y ASiC | [Firma PDF, CAdES, validación y revocación](18-pades-asic-s-y-revocacion.md) |
| TR-34 y PKCS#11 | [Distribución KDH/KRD, transporte RSA y perfiles de token](19-tr-34-pkcs11-y-transporte-rsa.md) |
| XAdES y WS-Security | [Firmas XML, validación, UsernameToken y SOAP con timestamp protegido](20-xades-y-ws-security-avanzados.md) |
| Formatos y PKCS#11 | [PEM, DER, PKCS#12, perfiles de token e inventario seguro](21-formatos-de-claves-almacenes-y-pkcs11.md) |
| Generar token PKCS#11 | [SoftHSM, objetos residentes, inventario, firma y diagnóstico](22-generar-un-token-pkcs11.md) |

## Cómo usar los ejemplos

- Copia los valores exactamente, sin saltos finales no indicados.
- Revisa bytes y formato antes de comparar salidas.
- Ejecuta siempre la prueba positiva y al menos una negativa.
- Los resultados aleatorios no se comparan por igualdad: se valida tamaño, estructura y round-trip.
- Las insignias **EXP** indican funciones experimentales.
