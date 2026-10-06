# JOSE capacidad 6 — PKIX local de x5c (encargo 73, fase 3)

## Estado y hueco

JoseJwkPolicy comprueba huellas y firmas entre certificados pero no confianza PKIX. La opción explícita de clave de cabecera está desactivada por defecto. Falta introducir anclas PEM y fecha, y presentar las comprobaciones por separado.

## Decisiones y alcance

Clase nueva JoseX5cValidation: valida el camino suministrado con CertPathValidator, PKIXParameters y revocación desactivada; no usa CertPathBuilder, CertStore, x5u ni jku. Llama a CertificateGenerator.parseCertificate y parseCertificateChain sin modificarlos. Resultado estable: formación, firmas por eslabón, vigencia, CA/longitud, uso de firma de hoja, clave verificadora, dos huellas y PKIX. Excepciones de proveedores se normalizan a códigos propios.

El coordinador JWT añade controles de anclas y fecha ISO-8601 (vacío=actual), detalles públicos y avisos EN/ES no modales. JOSEController solo campos FXML y delegación de etiquetas. No cambian su API pública ni pom.xml. Se retira únicamente el bloqueo por firmas de eslabones en la extracción explícita de x5c; el fallo de cadena se informa y permite la verificación explícita del token.

Tests con PKI inventada propia raíz/intermedia/hoja, fechas fijas, CA, longitud, uso, firmas, anclas múltiples, huellas y clave equivocada. Transcripción UI estable en ambos idiomas y tres perfiles; no contiene DER, números de serie, fechas dinámicas, huellas ni mensajes PKIX del JDK.

Referencias: [RFC 7515 §4.1.6–4.1.8](https://www.rfc-editor.org/rfc/rfc7515.html#section-4.1.6), [PKIXParameters Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html). La PKI inventada permite validar fechas y restricciones; el ejemplo x5c del apéndice B no aporta las anclas y claves privadas necesarias para estas variantes.

Test UI en rojo, 1 test/1 fallo/0 errores: `x5c trust anchors control is missing`. JavaFX real disponible. Log target/jose-cap-6-red.log.
