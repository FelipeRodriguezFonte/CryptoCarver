# Handoff: CF-15C - XAdES with PKCS#11 real token

## Objetivos Alcanzados
1. **Selector de Origen de Firma en UI:** 
   - Se reestructuró `handleSignXML` y `handleLoadXMLKeys` para separar condicionalmente la carga y firma por origen (KeyStore vs PKCS#11).
   - Ahora `handleLoadXMLKeys` obtiene la sesión de `Pkcs11SessionManager.getInstance().requireSession()` en lugar de KeyStore, y filtra explícitamente aquellos objetos que sean Claves Privadas (`java.security.PrivateKey`) *y* tengan un certificado asociado (`listPrivateKeysWithCertificate()`).
2. **Firma y Publicación XAdES:**
   - La nueva función llama a `XMLSignatureOperations.signXAdESWithPkcs11(...)`.
   - Se publica en el historial que el origen fue `PKCS#11` y el `Alias` usado, sin requerir ni imprimir password, path u otras credenciales sensibles.
3. **Encapsulamiento del KeyStore Público:**
   - Se eliminó totalmente `Pkcs11Session.getKeyStore()`. La clave privada subyacente no escapa de su integración JCA para SD-DSS.
4. **Validación Automática e Integración UI:**
   - `SoftHsmIntegrationTest.java` fue actualizado. Incluye filtrado estricto que requiere un alias AES (`CRYPTOCARVER_SOFTHSM_AES_ALIAS`) o explora la enumeración asegurando que es tipo "Secret key" y su algoritmo es "AES".
   - El test SoftHSM efectúa validación positiva de XAdES y aserción negativa al ser modificado maliciosamente el XML firmado.
5. **Pruebas Consolidadas:**
   - `mvn -q clean test` con todos los test SoftHSM y las suites UI ejecutadas pasan correctamente.
   - `git diff --check` y `xmllint` limpios y sin trailing whitespaces.

## Seguridad e Histórico
- El PIN nunca es cacheado a nivel de disco, y todo recae en la sesión en memoria del `SunPKCS11`.

## Próximos pasos
El entorno base de UI y firma XML está sellado. Podemos continuar hacia **CF-15D: CAdES / CMS con token PKCS#11**.
