# Process Designer — Fase 5B: expansión del catálogo de operaciones

Esta fase lleva al lienzo las familias de operaciones que CryptoCarver ya implementa y
que hoy no son alcanzables desde el Process Designer. **Es integración, no
implementación**: todas las fachadas criptográficas existen y están probadas en
`com.cryptocarver.crypto`.

**Requisito previo: la Fase 5A debe estar aprobada e integrada.** Todo nodo nuevo se
declara con el `NodeDescriptor` introducido en 5A y se registra en `NodeCatalog`. Si
añades un tipo de nodo ampliando las cadenas de comparación por tipo de
`ProcessDesignerController.select()` o `saveSelectedNodeSettings()`, la entrega se
rechaza sin más revisión.

La fase se divide en tres olas. **Cada ola se entrega, se audita y se integra por
separado.** No mezcles olas en una sola entrega.

---

## 1. Reglas que aplican a las tres olas

### 1.1 Reutilización obligatoria

- Todo handler nuevo **delega en una fachada existente**. Está prohibido reimplementar
  criptografía, codificación, derivación o formatos dentro de `model/process/handlers`.
- Si una fachada no expone la variante necesaria, se amplía la fachada (con sus pruebas
  propias) y el handler la llama. No se duplica lógica.

### 1.2 Sin red

Ningún nodo abre sockets. Quedan fuera OCSP/CRL en línea, descarga de cadenas y sellado
RFC 3161 contra TSA remota. Un `Socket`, `URLConnection` o `HttpClient` nuevo bajo
`model/process` es un hallazgo bloqueante.

### 1.3 Datos sensibles

Además de claves y contraseñas, en esta fase entran **PAN, PIN, pistas de banda
magnética y criptogramas**. Todos ellos:

- se declaran `sensitive = true` en el `NodeParameter` y por tanto no se serializan;
- no aparecen en trazas, mensajes de error ni en `NodeExecutionEvent.safeMessage`;
- se enmascaran en la tabla de ejecución y en los resultados según
  `SecretVisibilityProfile` (`FULL_LAB` / `MASKED` / `REDACTED`), igual que ya hacen los
  módulos de Payments.

### 1.4 Coherencia preflight / ejecución

`ProcessValidator` delega en `handler.validateConfiguration(node)`
(`ProcessValidator.java:200`). Cada nodo nuevo debe validar **exactamente lo mismo** en
preflight y en ejecución. Una regla que sólo exista en uno de los dos sitios es un
hallazgo bloqueante.

### 1.5 Representaciones

Cada nodo declara sus `PortDefinition` con las representaciones que realmente acepta.
Cuando una operación exige un formato concreto (por ejemplo hexadecimal para una clave o
decimal para un PAN), **el puerto lo exige y el motor lo rechaza antes de ejecutar**; no
se convierte en silencio.

### 1.6 i18n y accesibilidad

Las mismas reglas de la Fase 5A: nada de literales en inglés incrustados, todo por
`ModuleTextCatalog` y los dos ficheros `messages*.properties`, control oculto también
desgestionado, texto accesible en los controles nuevos.

---

## 2. Ola 5B.1 — Fontanería y representación

Sin estos nodos, muchas de las operaciones de las olas siguientes no se pueden encadenar.
Es la ola de menor riesgo y mayor efecto, y va primero.

| Tipo | Puertos de entrada | Salida | Fachada existente |
|---|---|---|---|
| `CONCAT` | `a`, `b` (extensible) | BINARY | fontanería, sin criptografía |
| `SLICE` | `input` | BINARY | fontanería; config `offset`, `length` |
| `PAD` / `UNPAD` | `input` | BINARY | `utils.PaddingUtil.addPadding` / `removePadding`, con `PaddingUtil.PaddingType` (PKCS5, PKCS7, ISO_9797_M1, ISO_9797_M2, ISO_7816_4, ZERO) y `blockSize` |
| `XOR` | `a`, `b` | BINARY | `KeyOperations.xor(byte[], byte[])` |
| `ASSERT_EQUALS` | `actual`, `expected` | reemite `actual` | `MACOperations.constantTimeEquals(byte[], byte[])` |
| `BASE32_ENCODE` / `_DECODE` | `input` | TEXT / BINARY | `codec.CodecRegistry.getInstance()` con `ByteFormat.BASE32` |
| `BASE58_ENCODE` / `_DECODE` | `input` | TEXT / BINARY | `CodecRegistry` con `ByteFormat.BASE58` |
| `BASE58CHECK_ENCODE` / `_DECODE` | `input` | TEXT / BINARY | `CodecRegistry` con `ByteFormat.BASE58_CHECK` |
| `EBCDIC_ENCODE` / `_DECODE` | `input` | BINARY / TEXT | `EBCDICConverter.encode(String, String)` / `decode(byte[], String)`; `codePage` desde `supportedCodePages()` |
| `COMPRESS` / `DECOMPRESS` | `input` | BINARY | `CompressionCodec.compress(byte[], String)` / `decompress`; formatos `gzip`, `zlib`, `deflate` |
| `CHARSET_CONVERT` | `input` | TEXT_UTF8 | juego de origen y destino |
| `ASN1_DECODE` | `input` | TEXT_UTF8 | `asn1.ASN1Parser.parse` + `asn1.ASN1TreeExporter.toJson` / `toMarkdown` |
| `CHECK_DIGIT_CALC` / `_VERIFY` | `input` | TEXT_UTF8 | `CheckDigitCalculator.calculateCheckDigit` / `validateCheckDigit`; algoritmos exactos de `SUPPORTED_ALGORITHMS`: `Luhn (Mod 10)`, `Verhoeff`, `Damm` |
| `MODULAR_ARITHMETIC` | `a`, `b` (opcional) | TEXT_UTF8 | `ModularArithmetic` |
| `UUID_GENERATE` | — | TEXT_UTF8 | `UUIDGenerator.generateUUID()` / `generateUUIDWithoutHyphens()` |
| `BYTE_STATISTICS` | `input` | TEXT_UTF8 | `ByteStatistics.analyze(byte[])` |

Todas las fachadas de esta ola existen ya y están probadas. `CONCAT` y `SLICE` son las dos
únicas piezas sin fachada, y no contienen criptografía.

**`ASSERT_EQUALS` es obligatorio y no negociable.** Compara en tiempo constante; si
difiere, emite `ERROR`, detiene el proceso y **no revela el valor esperado ni el
obtenido** más allá de la longitud y la representación. Con él, un `.cfprocess.json` se
convierte en una prueba ejecutable, que es como vamos a validar las olas siguientes.

---

## 3. Ola 5B.2 — Claves y pagos

Esta ola suma 41 tipos, casi el doble que la 5B.1, y es la que introduce PAN, PIN, pistas
de banda magnética y criptogramas. **Se entrega y se audita en dos bloques separados:**

- **5B.2a — Claves** (§3.1, 20 tipos). Va primero: los nodos de pagos consumen material de
  clave, KCV y paridad que produce este bloque.
- **5B.2b — Pagos** (§3.2, 21 tipos). Bloque propio, para que el material sensible reciba
  una ronda de auditoría entera y no se revise en la cola de un diff de 41 nodos.

### 3.1 Claves — bloque 5B.2a

| Tipo | Fachada |
|---|---|
| `KCV` (`VISA`, `IBM`, `ATALLA`, `ATALLA_R`, `FUTUREX`, `CMAC`, `AES`, `SHA256`, bloque cero completo) | `KeyOperations.calculateKCV_VISA/_IBM/_ATALLA/_ATALLA_R/_FUTUREX/_CMAC/_AES/_SHA256(byte[])` y `calculateFullZeroBlockKCV(byte[], String)` |
| `KEY_SPLIT_XOR` / `KEY_COMBINE_XOR` | `KeyOperations.splitKey(byte[], int)`, `combineKeyComponents(byte[][])` |
| `PARITY_ADJUST` / `PARITY_CHECK` | `KeyOperations.applyOddParity(byte[])` (muta en sitio), `detectParity(byte[])` → `ParityType` |
| `KDF_HKDF`, `KDF_SP800_108`, `KDF_X963`, `KDF_SCRYPT`, `KDF_ARGON2` | `KeyDerivation.hkdf`, `sp800108Counter`, `x963`, `scrypt`, `argon2` |
| `AES_KEYWRAP_3394` / `_UNWRAP_3394`, `AES_KEYWRAP_5649` / `_UNWRAP_5649` | `KeyWrapOperations.wrapRfc3394` / `unwrapRfc3394` / `wrapRfc5649` / `unwrapRfc5649`, todas `(byte[] kek, byte[])` |
| `TR31_WRAP` / `TR31_UNWRAP` / `TR31_PARSE_HEADER` | `TR31Operations.wrapKey(String,String,String,char,char,char,char)`, `unwrapKey(String,String)`, `parseHeader(String)` |
| `ICSF_TOKEN_PARSE` (sólo lectura) | `crypto.icsf.IcsfTokenParser` → `IcsfTokenReport` |
| `KEYPAIR_GENERATE` (RSA, DSA, ECDSA, EdDSA) — generaliza el actual `RSA_KEYPAIR_GENERATE` | `AsymmetricKeyOperations` |
| `KEY_MATERIAL_INSPECT` | `KeyMaterialInspector.describeKey(java.security.Key)` |

`RSA_KEYPAIR_GENERATE` se conserva como alias del nuevo `KEYPAIR_GENERATE` para no romper
procesos guardados.

### 3.2 Pagos — bloque 5B.2b

| Tipo | Fachada |
|---|---|
| `PIN_BLOCK_ENCODE` / `PIN_BLOCK_DECODE` (ISO-0/1/2/3/4) | `PaymentOperations.encodePinBlock`, `decodePinBlock`, `encodePinBlockISO4WithClear` |
| `PIN_BLOCK_TRANSLATE` | `PaymentOperations.translatePinBlock` |
| `CVV_GENERATE` / `CVV_VERIFY` | `PaymentOperations.generateCVV`, `verifyCVV` |
| `DCVV_GENERATE` / `DCVV_VERIFY` | `PaymentOperations.generateDCVV`, `verifyDCVV` |
| `PVV_GENERATE` / `PVV_VERIFY` | `PaymentOperations.generatePVV`, `verifyPVV` |
| `IBM3624_OFFSET` | `PaymentOperations.generateIBM3624Offset` |
| `DUKPT_TDES_DERIVE` / `DUKPT_AES_DERIVE` | `DukptKsn.deriveWorkingKey`, `AesDukpt.deriveWorkingKey` |
| `DUKPT_PIN_CRYPT` | `AesDukpt.cryptPinBlock` |
| `EMV_ICC_MASTER_KEY` | `EMVOperations.deriveICCMasterKey` |
| `EMV_SESSION_KEY` | `EMVOperations.deriveSessionKey` |
| `EMV_ARQC_GENERATE` / `EMV_ARQC_VERIFY` | `EMVOperations.generateARQC`, `verifyARQC` |
| `EMV_ARPC` (métodos 1 y 2) | `EMVOperations.generateARPC_Method1`, `_Method2` |
| `EMV_TLV_PARSE` | `EmvTlv.analyze` |
| `TRACK2_ENCODE` / `TRACK2_PARSE` | `PaymentOperations.encodeTrack2`, `parseTrack2` |

Los nodos de pagos deben rechazar PAN con dígito de control inválido **antes** de operar,
salvo que exista un conmutador explícito de laboratorio y visible en el inspector.

---

## 4. Ola 5B.3 — Sobres y firmas

| Tipo | Fachada |
|---|---|
| `JWS_SIGN` / `JWS_VERIFY`, `JWS_DETACHED_SIGN` / `_VERIFY` | `JOSEService` |
| `JWE_ENCRYPT` / `JWE_DECRYPT` | `JOSEService` |
| `JWT_INSPECT` (sólo lectura, sin verificar) | `JOSEService` |
| `COSE_SIGN1` / `COSE_VERIFY1` | `COSEOperations.sign1`, `verify1` |
| `COSE_MAC0` / `COSE_VERIFY_MAC0` | `COSEOperations.mac0`, `verifyMac0` |
| `COSE_ENCRYPT0` / `COSE_DECRYPT0` | `COSEOperations.encrypt0`, `decrypt0` |
| `CMS_SIGN` / `CMS_VERIFY` | `CMSOperations.generateSignedData`, `verifySignedData` |
| `CMS_ENVELOPE` / `CMS_DEVELOPE` | `CMSOperations.generateEnvelopedData` |
| `CADES_BES_SIGN` | `CMSOperations.generateCadesBes` |
| `XMLDSIG_SIGN` / `XMLDSIG_VERIFY` (XAdES) | `XMLSignatureOperations.signXAdES`, `verifyXAdES` |
| `PADES_SIGN` (perfiles B / T / LT / LTA) / `PADES_VERIFY` | `PadesOperations` |
| `OPENPGP_ENCRYPT` / `_DECRYPT` / `_SIGN` / `_VERIFY` | `OpenPgpOperations` |
| `PQC_KEYPAIR_GENERATE`, `PQC_SIGN`, `PQC_VERIFY` | `PostQuantumOperations` |
| `PQC_KEM_ENCAPSULATE` / `PQC_KEM_DECAPSULATE` | `PostQuantumOperations.encapsulate`, `decapsulate` |
| `CERT_PARSE` | `KeyMaterialInspector.describeCertificate` |
| `CERT_SELF_SIGNED_GENERATE` | `CertificateGenerator.generateSelfSignedCertificate` |
| `CERT_VALIDATE` (offline, contra truststore local) | `CertificateLinter`, `RevocationOperations` en modo offline |

Los perfiles PAdES `T` / `LT` / `LTA` y cualquier variante CAdES con sello de tiempo
**sólo se admiten con token de sello aportado como fichero local**. Si la única vía es
consultar una TSA por red, ese perfil queda fuera de la ola y se documenta como
limitación explícita.

Queda fuera de las tres olas: TR-34, ASiC, PKCS#11 dentro de un nodo, y cualquier
operación que exija hardware o red.

---

## 5. Pruebas obligatorias

Sin red, sin TSA, sin recursos dependientes de la máquina. Reutiliza los vectores de
prueba que ya existen en las suites de cada módulo; no inventes vectores nuevos donde ya
hay uno conocido.

### 5.1 Por cada tipo de nodo — las tres son obligatorias

1. **Vector conocido**: el nodo, ejecutado con una configuración fija, produce el
   resultado esperado.
2. **Rechazo seguro**: configuración incompleta o inválida, o representación de entrada
   incorrecta, falla en `ProcessEngine.validate()` **antes de ejecutar**, con mensaje sin
   datos sensibles.
3. **Integración en el motor**: el nodo funciona dentro de un grafo real, encadenado con
   entradas y salidas.

### 5.2 Puerta anti-reimplementación

`HandlerFacadeParityTest`: para cada nodo, el resultado del handler debe ser **idéntico**
al de invocar directamente la fachada con los mismos parámetros. Esta prueba es la
defensa contra que se reimplemente criptografía dentro del handler.

### 5.3 Puerta de cobertura de la ola

`NodeCatalogCoverageTest`: el conjunto de tipos declarados en `NodeCatalog` contiene
**todos** los tipos listados en la tabla de la ola entregada. La prueba enumera la lista
esperada de forma literal, de modo que una entrega parcial falla de forma mecánica y no
depende de que el revisor lo note.

### 5.4 Secretos

Extender `ProcessDefinitionSecretsTest` de la Fase 5A a los tipos nuevos: rellenar todo
parámetro sensible (incluidos PAN, PIN, pistas y criptogramas), serializar y afirmar que
ninguno aparece en el JSON.

Añadir `ProcessTelemetryRedactionTest`: ningún `NodeExecutionEvent.safeMessage`, ninguna
fila de la tabla de ejecución y ningún mensaje de error contiene PAN, PIN ni clave, en
los tres perfiles de visibilidad.

### 5.5 Procesos de extremo a extremo

Entregar procesos de ejemplo, sin secretos, en `src/test/resources/process/`, que se
ejecuten en las pruebas y usen `ASSERT_EQUALS` para comprobarse a sí mismos:

- Ola 1: `texto → SHA-256 → HEX_ENCODE → ASSERT_EQUALS(digest conocido)`.
- Ola 2: `PAN + clave CVK → CVV_GENERATE → ASSERT_EQUALS(CVV conocido)` y
  `clave → KCV(VISA) → ASSERT_EQUALS(KCV conocido)`.
- Ola 3: `carga → COSE_SIGN1 → COSE_VERIFY1 → ASSERT_EQUALS(carga original)`.

### 5.6 No regresión

Todo lo listado en la Fase 5A §4.4 debe seguir en verde, más las suites propias de las
fachadas tocadas.

---

## 6. Documentación

- Actualizar `docs/OPERATIONS_CATALOG.md` con todos los tipos de nodo nuevos.
- Actualizar `docs/process-designer-architecture.md` con las familias añadidas.
- Crear `docs/process-designer-operation-reference.md`: por cada nodo, puertos,
  representaciones, parámetros, fachada que envuelve y limitaciones conocidas.
- Actualizar `README.md` y `CHANGELOG.md`.

---

## 7. Entrega y evidencia

Por cada ola, entregar:

1. Tabla de tipos entregados frente a la tabla pedida, con las diferencias justificadas.
2. Fachada concreta que envuelve cada nodo, con archivo y método.
3. Fachadas ampliadas, si las hubo, y por qué no bastaba lo existente.
4. Decisiones sobre datos sensibles y sobre representaciones exigidas por puerto.
5. Resultado literal de:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   mvn -o test
   mvn -o -DrunUiTests=true -Dtest.mode=true test
   git diff --check
   ```

6. Confirmación explícita de que ninguna clase bajo `model/process` abre sockets.

**No declares una ola completa** si falta cualquiera de: un tipo de su tabla, la prueba de
paridad con la fachada, la prueba de cobertura del catálogo, la prueba de secretos o los
procesos de extremo a extremo autoverificados.

---

## 8. Regla de trabajo

Para cada nodo: implementar sobre la fachada, declarar el descriptor, añadir prueba
positiva con vector conocido, añadir prueba de rechazo seguro, añadir prueba de
integración en el motor, ejecutar y aportar evidencia.

Si una fachada no permite implementar un nodo de la tabla sin reimplementar
criptografía, **detente y preséntalo antes de codificar**. Es preferible entregar la ola
con un nodo documentado como pendiente que entregarla con una implementación paralela y
sin auditar.
