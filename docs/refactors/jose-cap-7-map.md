# JOSE capacidad 7 — cabeceras JWS detached (encargo 73, fase 4)

## Estado y hueco

JWS normal usa withCustomJwsHeader; detached construye directamente la cabecera y carece de editor. Compact, Flattened JSON y General JSON ya existen. El verificador General elige la primera firma del algoritmo seleccionado.

## Decisiones y alcance

Añadir editor JSON protegido y etiqueta en JoseJwtCoordinator/FXML; JOSEController solo campos FXML. La API pública del controlador no cambia. JOSEService añade sobrecarga compatible de composición detached y reutiliza withCustomJwsHeader para ambas serializaciones. alg/b64/crit permanecen reservadas: la casilla b64=false crea crit=[b64] como en el compositor normal. kid, typ, cty, x5c, x5t, x5t#S256, jwk pública y cabeceras propias se editan en JSON. Se rechaza material privado/simétrico en jwk embebida, conforme al uso de clave pública en RFC 7515 §4.1.3, para proteger salida y capturas.

Se conserva la semántica existente de verificación de una firma matching algoritmo en General JSON; se comprueba selección por kid con JWKS. No se amplía el encargo a una política de todas las firmas. alg=none mantiene su limitación de cabeceras personalizadas igual que normal.

Tests: UI real con los controles, EN/ES, perfiles, errores reservados legibles y avisos b64=false; integridad de cabeceras y contenido detached en tres serializaciones. Vector exacto [RFC 7797 §4.2](https://www.rfc-editor.org/rfc/rfc7797.html#section-4.2), control §4.1, clave HMAC de [RFC 7515 A.1](https://www.rfc-editor.org/rfc/rfc7515.html#appendix-A.1); solo material publicado/inventado. Cabeceras de certificados usan la PKI inventada de fase 3. No se fijan firmas ni DER aleatorias en el digest.

Ficheros: JOSEService.java (autorizado), JoseJwtCoordinator.java, campos JOSEController.java, jose.fxml, test nuevo JoseDetachedHeadersCharacterizationUITest y test criptográfico JoseDetachedHeadersTest. Sin dependencias ni cambios en clases prohibidas.

Test UI en rojo, 1 test/1 fallo/0 errores: `Detached protected header editor is missing`. Log target/jose-cap-7-red.log.
