# Process Designer — Phase 4: WS-Security (firma y verificación SOAP)

Implementa WS-Security como una capacidad nueva del Process Designer, sobre las fases 2 y 3 ya aprobadas. Esta fase se limita a firmar y verificar mensajes SOAP con WS-Security X.509. No regreses el modelo `FlowValue`, los contratos de puertos, el manejo de secretos, la serialización segura ni la UX de nodos criptográficos existente.

> Importante: XMLDSig/XAdES existente en `XMLSignatureOperations` no equivale a WS-Security. No reutilizarlo como si generase un `wsse:Security` válido. Para WS-Security utilizar una librería especializada y mantenida, preferentemente Apache WSS4J DOM, integrada de forma acotada y documentada.

## 1. Objetivo

Añadir dos nodos ejecutables a Process Designer:

- `WSS_SIGN`: recibe un SOAP XML y devuelve el mismo SOAP con cabecera `wsse:Security`, `BinarySecurityToken` X.509 y firma WS-Security.
- `WSS_VERIFY`: recibe un SOAP XML firmado y devuelve el SOAP original si la firma y las políticas configuradas son válidas; si no, falla el nodo y detiene el proceso.

La operación es local y offline. No se envía SOAP a ningún endpoint ni se implementa un cliente HTTP/SOAP en esta fase.

## 2. Alcance obligatorio

### 2.1 Formatos aceptados

- Aceptar únicamente SOAP 1.1 (`http://schemas.xmlsoap.org/soap/envelope/`) y SOAP 1.2 (`http://www.w3.org/2003/05/soap-envelope`).
- `WSS_SIGN` y `WSS_VERIFY` aceptan exclusivamente `TEXT_UTF8`.
- Ambos producen `TEXT_UTF8`.
- Rechazar XML no SOAP, XML mal formado, documentos con DTD, entidades externas o referencias externas antes de cualquier operación criptográfica.

### 2.2 WSS_SIGN

Implementar perfil inicial interoperable:

- WS-Security 1.1 X.509 Certificate Token Profile.
- `wsse:BinarySecurityToken` conteniendo el certificado X.509 codificado en Base64.
- XML Signature enveloped con referencia al `SOAP Body` y al `wsu:Timestamp` si se activa.
- `SHA256withRSA` obligatorio.
- Canonicalización exclusiva: Exclusive XML Canonicalization.
- Digest obligatorio: SHA-256.
- Incluir `wsu:Id` en los elementos firmados.
- Timestamp opcional, activado por defecto:
  - `Created` UTC.
  - `Expires` UTC configurable, por defecto cinco minutos.

Configuración no sensible persistible:

- ruta de keystore;
- tipo `PKCS12`/`JKS`;
- alias;
- algoritmo;
- activar timestamp;
- duración de timestamp;
- versión SOAP detectada o forzada, si se necesita.

Configuración sensible, nunca persistible:

- contraseña de keystore;
- contraseña de clave;
- PIN;
- clave privada.

### 2.3 WSS_VERIFY

Implementar verificación local del mismo perfil:

- localizar y validar `wsse:Security`;
- exigir `ds:Signature`;
- verificar las referencias firmadas;
- exigir que el SOAP Body esté firmado;
- si se configura timestamp, exigirlo y validar Created/Expires contra reloj UTC;
- validar el certificado firmante contra truststore opcional;
- si no hay truststore, permitir únicamente modo explícito `STRUCTURAL_ONLY` para inspección, con advertencia visible; el modo por defecto debe ser `TRUSTED_CERTIFICATE`.

Resultado:

- válido: el nodo devuelve el XML SOAP de entrada sin alterar y emite evento `SUCCESS`.
- inválido o no confiable: error de validación seguro, evento `ERROR` y se detiene el workflow.
- No devolver `VALID` como texto: el paso siguiente debe recibir el SOAP ya validado, no un booleano.

## 3. Seguridad obligatoria

### 3.1 Dependencias y diseño

- No implementar WS-Security manualmente con DOM, XPath y JCA.
- Añadir la dependencia especializada mínima necesaria (Apache WSS4J DOM) con versión fija y compatible con Java 17/BouncyCastle/Santuario existentes.
- No añadir dependencias SOAP de servidor o transporte: no hay red en esta fase.
- Crear una fachada dedicada, por ejemplo `WsSecurityOperations`, separada de los handlers y de la UI.

### 3.2 Protección XML

Toda carga XML debe:

- deshabilitar DTD;
- deshabilitar external general entities;
- deshabilitar external parameter entities;
- deshabilitar XInclude;
- aplicar `FEATURE_SECURE_PROCESSING`;
- no resolver recursos externos.

Las validaciones deben proteger contra XML Signature Wrapping:

- comprobar que la referencia firmada apunta al único SOAP Body efectivo;
- no aceptar por XPath ambiguo un Body duplicado;
- verificar IDs únicos y referencias internas;
- rechazar referencias externas.

### 3.3 Algoritmos y políticas

Permitir únicamente en esta fase:

- RSA-SHA256;
- SHA-256;
- Exclusive C14N.

Rechazar explícitamente SHA-1, MD5, DSA, RSA v1.5 encryption, transformaciones externas y algoritmos no permitidos. No mostrar en UI opciones no implementadas.

### 3.4 Secretos

Aplicar exactamente la política de Phase 3:

- `PasswordField` para contraseñas;
- valores sensibles no se serializan, no se registran y no se muestran en errores;
- arrays temporales de bytes/chars se limpian en `finally` cuando sea viable;
- al reabrir un proceso, exigir reintroducir credenciales antes de ejecutar.

## 4. Modelo y motor

- Añadir `WSS_SIGN` y `WSS_VERIFY` a un handler especializado o a un handler XML/WSS separado; no ampliar indiscriminadamente `AdvancedCryptoNodeHandler`.
- Ambos nodos tienen un único puerto obligatorio `payload` con `TEXT_UTF8`.
- Usar `ProcessNodeHandler.validateConfiguration()` para validar antes de ejecutar:
  - tipo de nodo;
  - algoritmos permitidos;
  - ruta y tipo de keystore/truststore;
  - alias;
  - duración de timestamp razonable;
  - modo de confianza.
- La validación no debe revelar contraseñas ni contenidos de certificados.
- Versionar procesos solo si se modifica el modelo persistido. Mantener compatibilidad con v1/v2/v3.

## 5. UX

### 5.1 Paleta

Crear una categoría `XML & WS-Security` o un submenú dentro de Crypto:

- `WS-Security sign SOAP`
- `WS-Security verify SOAP`

No mostrar nodos futuros como activos.

### 5.2 Inspector contextual

Para `WSS_SIGN`:

- algoritmo fijo visible como RSA-SHA256;
- ruta keystore y botón Browse;
- tipo keystore;
- alias;
- contraseña de keystore y de clave (`PasswordField`);
- checkbox `Add Timestamp`, por defecto activo;
- duración en segundos, por defecto 300;
- aviso de secretos no persistibles.

Para `WSS_VERIFY`:

- modo de confianza `TRUSTED_CERTIFICATE` / `STRUCTURAL_ONLY`;
- ruta truststore y Browse, obligatoria en `TRUSTED_CERTIFICATE`;
- tipo truststore;
- contraseña truststore (`PasswordField`);
- checkbox `Require Timestamp`, activo por defecto;
- tolerancia de reloj configurable y limitada, por defecto 60 segundos;
- advertencia prominente si se selecciona `STRUCTURAL_ONLY`.

No reutilizar de forma confusa controles de Sign genérico: agrupar campos WSS completos, con etiquetas propias, y ocultarlos mediante `visible=false` y `managed=false` cuando no correspondan.

### 5.3 Canvas y resultados

- Etiquetas de nodo: `WSS Sign (RSA-SHA256)` y `WSS Verify`.
- Mostrar badge `TEXT_UTF8`.
- En resultados, mostrar metadatos seguros: versión SOAP, certificado subject/issuer, timestamp validado y elementos firmados. Nunca contraseñas, claves, XML completo sensible o stack traces.

## 6. Pruebas obligatorias

No usar red, TSA, servicios SOAP externos ni recursos dependientes de la máquina.

### 6.1 Operaciones WSS

- Fixture SOAP 1.1 y SOAP 1.2 mínimo.
- Firmar y verificar ambos fixtures con keystore/truststore temporal o fixtures de test controlados.
- Comprobar que el resultado incluye `wsse:Security`, `BinarySecurityToken`, `ds:Signature`, Body firmado y Timestamp cuando está activo.
- SOAP modificado tras firma: falla verificación.
- Timestamp expirado: falla verificación.
- Certificado no confiable: falla en `TRUSTED_CERTIFICATE`.
- XML sin firma: falla.
- SOAP con Body duplicado/ataque de wrapping: falla.
- Documento con DOCTYPE o entidad externa: falla sin resolverla.
- Algoritmo SHA-1 o referencia externa: falla.

### 6.2 Process Engine

- `CONSOLE_INPUT (SOAP) → WSS_SIGN → WSS_VERIFY → CONSOLE_OUTPUT` pasa y conserva un SOAP válido.
- Input `BINARY` a WSS_SIGN/WSS_VERIFY falla en `ProcessEngine.validate()` antes de ejecución.
- Credenciales/keystore incompletos fallan antes de que un File output pueda escribir.
- Serialización de ambos nodos no contiene ninguna contraseña, PIN, clave ni contenido certificado sensible.

### 6.3 JavaFX

- Cargar FXML y añadir ambos nodos desde la paleta.
- Verificar inspector contextual y `PasswordField`.
- Configurar WSS_SIGN desde UI y verificar los valores no sensibles almacenados.
- Guardar un proceso y comprobar que las contraseñas no aparecen en JSON.
- Reabrirlo, comprobar que los campos de contraseña están vacíos y que ejecutar solicita reintroducción.
- Crear flujo de firma/verificación desde UI y ejecutarlo de forma asíncrona con fixture local.

## 7. Documentación

Crear `docs/process-designer-phase-4-wss-security.md` con:

- perfil WS-Security soportado y versiones SOAP;
- algoritmos permitidos;
- trust modes y sus riesgos;
- amenazas mitigadas (XXE, referencias externas, wrapping, algoritmos débiles);
- política de timestamp;
- ejemplos no sensibles de workflow;
- limitaciones explícitas.

Actualizar `docs/process-designer-architecture.md` y `docs/OPERATIONS_CATALOG.md` si procede.

## 8. Fuera de alcance

No implementar en esta fase:

- SOAP HTTP client/server;
- UsernameToken;
- XML Encryption / `xenc:EncryptedData`;
- SAML tokens;
- Kerberos;
- WS-Trust / WS-SecureConversation;
- firma de attachments MTOM;
- PKCS#11/HSM dentro del nuevo nodo;
- WS-SecurityPolicy completa;
- algoritmos SHA-1 o perfiles legacy.

## 9. Entrega y evidencia

Antes de declarar la fase completada, entregar:

1. Archivos modificados y motivo.
2. Dependencia WSS elegida, versión y justificación.
3. Operaciones existentes reutilizadas y nuevas fachadas creadas.
4. Decisiones de seguridad, especialmente XXE, wrapping, trust y timestamps.
5. Resultado literal de:

   ```bash
   mvn test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest,OperationRegistryTest,WsSecurityOperationsTest
   mvn compile test
   git diff --check
   ```

6. Toda prueba externa fallida debe indicarse separadamente con causa y evidencia.
7. No afirmar que WSS está implementado si falta cualquiera de: firma interoperable, verificación con confianza, defensa XXE/wrapping, no persistencia de secretos o pruebas de manipulación.

## 10. Regla de trabajo

Para cada requisito: implementar, añadir prueba positiva, añadir prueba de rechazo seguro, ejecutar y aportar evidencia. Si la dependencia WSS propuesta no puede garantizar las defensas solicitadas, detenerse y presentar alternativas antes de codificar. No sustituir WS-Security por XAdES/XMLDSig genérico ni simular validación.
