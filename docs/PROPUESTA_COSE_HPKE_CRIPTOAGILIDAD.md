# Propuesta: COSE y HPKE — cerrando el hueco de formatos de salida agile

## Contexto

Pregunta que motiva esta propuesta: *"¿Qué nos falta para completar a nivel
criptográfico? ¿Hemos evaluado ya cómo añadir el COSE, u otros estándares?
Formatos de salida de información cifrada que faciliten la criptoagilidad."*

Inventario real del repo (no de memoria) antes de proponer nada: CryptoCarver
ya tiene JOSE completo (JWS Compact/Flattened/General JSON, JWE compacto,
JWK/JWKS con rotación), CMS/PKCS#7 (tres superficies: TR-34 interno, un tab
"CMS" independiente, y un inspector embebido en Certificados), OpenPGP
completo, PAdES/XAdES/ASiC, PQC con ML-KEM/ML-DSA/SLH-DSA (los tres ya
estandarizados por NIST), y el `CryptoEnvelope` propio (JSON/compacto estilo
JWE, con lista blanca de algoritmos).

Búsqueda exhaustiva de términos en todo `src/`: **cero** menciones de COSE,
CBOR, HPKE, PASETO, `age`, WebAuthn, SAML, TUF, Sigstore, in-toto, S/MIME.
De esa lista, dos formatos encajan genuinamente con "salida cifrada agile"
(los demás son protocolos de ecosistema — atestación, cadena de suministro —
una categoría distinta a lo que es CryptoCarver hoy, o directamente lo
contrario de agile por diseño, como PASETO/`age`, que fijan un único
algoritmo a propósito).

## Fase A — COSE (RFC 9052 / RFC 9053)

### Por qué esta primero

Es el hermano CBOR de JOSE: mismo modelo mental (`alg` por mensaje,
firma/cifrado/MAC desacoplados del transporte) que la app ya tiene completo
y probado. La curva de implementación es baja porque **reutiliza las mismas
primitivas** ya presentes (ECDSA, EdDSA, HMAC, AES-GCM) — lo único nuevo es
la capa de serialización binaria (CBOR) y el registro de identificadores
numéricos de algoritmo de IANA en vez de strings `"alg"`. Es además el
formato real detrás de WebAuthn/FIDO2, EAT, mDL (carné de conducir móvil
ISO 18013-5) y C2PA — no es un ejercicio académico.

### Honestidad sobre el alcance

MVP cubre **COSE_Sign1** (firmante único), **COSE_Mac0** (MAC simétrico) y
**COSE_Encrypt0** (cifrado sin gestión de destinatarios), que es el
equivalente exacto de lo que JOSE Compact ya cubre en esta app. **No** cubre
en el MVP `COSE_Sign`/`COSE_Encrypt` multi-destinatario (el equivalente a
JWE/JWS JSON General, que tampoco está implementado hoy para JOSE) — se deja
anotado como extensión de fase A si hace falta más adelante, mismo criterio
que "empezar solo con el perfil simple" ya aplicado en TR-34 one-pass.

**Librería — verificado en Maven Central, no asumido:**
`com.augustcellars.cose:cose-java` (Jim Schaad, coautor del RFC, construida
sobre BouncyCastle — ya dependencia del proyecto) está publicada en Maven
Central en versión **1.1.0**, confirmado directamente. Es la elección para
esta fase. Existe una alternativa más moderna,
[`diggsweden/cose-lib`](https://github.com/diggsweden/cose-lib) (también
sobre BouncyCastle + PeterO CBOR), pero al momento de escribir esto su
publicación en Maven Central todavía está pendiente ("plan to publish...
in the near future" según su propio repositorio) — no se usa por esa razón,
se deja anotada como posible actualización futura si se publica y madura.

### Diseño

- **`COSEOperations.java`** (nuevo, `crypto/`): `sign1`/`verify1`,
  `mac0`/`verifyMac0`, `encrypt0`/`decrypt0`, generación/inspección de
  `COSE_Key` (EC2 para EC, OKP para Ed25519/X25519, Symmetric para
  HMAC/AES). Reutiliza las claves ya soportadas por
  `AsymmetricKeyOperations`/`KeyOperations` donde aplique — no reinventa
  generación de claves.
- **Algoritmos** (identificador COSE numérico → nombre igual que en JOSE
  para que el usuario reconozca el mapeo): `ES256/384/512`, `EdDSA`,
  `HMAC 256/384/512`, `A128GCM/A192GCM/A256GCM`.
- **UI**: pane nuevo "COSE", mismo patrón visual y de pestañas que
  `jose.fxml` (Sign/Verify, Encrypt/Decrypt, COSE_Key), salida en hex (el
  mensaje CBOR es binario, mismo tratamiento que ya se da a CMS/PKCS#7
  binario hoy — hex o base64, no texto crudo).
- **Registro**: nueva entrada en `OperationRegistry`/`UiNavigationRegistry`,
  categoría propia junto a JOSE (no dentro de Keys — es un formato de
  mensaje, mismo nivel que la pestaña JOSE actual).

### Ficheros

**Nuevos:** `crypto/COSEOperations.java`, `test/.../COSEOperationsTest.java`,
`fxml/cose.fxml`, `ui/COSEController.java`.
**Modificados:** `pom.xml` (dependencia COSE/CBOR), `OperationRegistry.java`,
`UiNavigationRegistry.java`, `messages*.properties`, `main-view-modern.fxml`
(o el fichero que aloje el nuevo tab de nivel superior).

### Verificación

RFC 9052 no publica un apéndice de vectores de prueba tan extenso como
JOSE/JWE, pero sí hay implementaciones de referencia interoperables
(la propia librería de Schaad incluye su suite de tests contra el RFC) —
el criterio es el mismo que en Fase A de RSA: si la librería base ya está
verificada contra el estándar, no hace falta reinventar esa verificación,
solo probar el envoltorio de esta app contra ella (round-trip
sign/verify/encrypt/decrypt con claves generadas en la propia app, y contra
un vector conocido de la librería si publica alguno).

---

## Fase B — HPKE (RFC 9180) + KEM híbrido clásico+PQC

### Por qué esta segunda, y por qué vale la pena

Este es, en mi opinión, el hueco con más valor criptográfico real, más allá
de "añadir un formato más". Hoy el panel PQC tiene ML-KEM implementado como
demo aislada de encapsular/desencapsular (`PostQuantumOperations.java`) —
sin ninguna envoltura que lo convierta en cifrado de mensajes de verdad.
HPKE es exactamente esa envoltura: define KEM+KDF+AEAD como una terna
intercambiable — la definición literal de agilidad criptográfica — y es el
esquema detrás de MLS (Messaging Layer Security), Encrypted Client Hello de
TLS 1.3, y los diseños actuales de cifrado post-cuántico híbrido.

**Punto a favor que no tienen ni TR-34 ni EMV Option B:** RFC 9180 sí
publica vectores de prueba oficiales en su Apéndice A (KEM, KDF, AEAD, y
casos base/PSK/auth) — aquí no hay que conformarse con "inspirado en", se
puede verificar bit a bit contra el estándar real. Esto encaja directamente
con el criterio de este proyecto de no implementar sin poder verificar.

### Honestidad sobre el alcance

- **Modo base HPKE con KEM clásico (X25519 o P-256) está 100% estandarizado
  (RFC 9180)** — esto se implementa contra el RFC final, con sus vectores.
- **HPKE con KEM post-cuántico o híbrido (X25519+ML-KEM) NO es RFC todavía**
  — vive en un draft IETF activo (`draft-irtf-cfrg-hpke-pq`), en evolución.
  Si se implementa, se etiqueta explícitamente como "basado en un draft
  IETF, no en un RFC final — el diseño puede cambiar antes de estandarizarse
  del todo", mismo estilo de aviso que ya usa el código para OIDs
  pre-estándar de ML-KEM/ML-DSA (`PostQuantumOperations.java`). No se
  presenta como "el estándar híbrido PQC", porque todavía no existe uno.
  Sugiero **dejar el KEM híbrido como extensión opcional de esta fase**
  (Fase B2), y entregar primero HPKE clásico (ya con valor real y
  verificable) + una integración *directa* (no-HPKE, más simple) de ML-KEM
  como "KEM del sobre" reusando `CryptoEnvelope` — así el ML-KEM ya
  implementado obtiene un uso real de inmediato sin esperar a que el draft
  se asiente.

### Diseño

- **`HPKEOperations.java`** (nuevo, `crypto/`): `seal`/`open` (API
  single-shot de RFC 9180 §5), modo base (`mode_base`) primero; `PSK`/`auth`
  modes como extensión si hace falta. KEM: `DHKEM(X25519, HKDF-SHA256)`
  inicialmente (el más simple e interoperable). KDF: `HKDF-SHA256/384/512`.
  AEAD: `AES-128-GCM`, `AES-256-GCM`, `ChaCha20Poly1305`. Todas estas
  primitivas ya existen en la app vía JCA/BouncyCastle — HPKE es
  principalmente composición (KDF de derivación de secreto + contexto de
  cifrado), no una primitiva nueva, así que el riesgo de implementación es
  bajo comparado con COSE (que sí necesita una capa de serialización nueva).
- **UI**: pane nuevo "HPKE" — Sender (Seal: cifra bajo la clave pública del
  receptor, produce `enc` + ciphertext) y Receiver (Open: descifra con su
  clave privada + `enc`).
- **Fase B2 (opcional, tras evaluar el draft):** variante con KEM ML-KEM,
  etiquetada como experimental/pre-estándar.

### Ficheros

**Nuevos:** `crypto/HPKEOperations.java`, `test/.../HPKEOperationsTest.java`
(con los vectores del Apéndice A de RFC 9180 como test fijo, no solo
round-trip), `fxml/hpke.fxml`, `ui/HPKEController.java`.
**Modificados:** `OperationRegistry.java`, `UiNavigationRegistry.java`,
`messages*.properties`.

### Verificación

- Test dedicado contra al menos un vector oficial del Apéndice A de RFC 9180
  (KEM X25519, HKDF-SHA256, AES-128-GCM) — confirma la implementación bit a
  bit, no solo que "descifra lo que él mismo cifró".
- Round-trip adicional con claves generadas en la propia app.
- `mvn -q -o test` completo sin regresiones.
- Verificación visual del panel tras `package_macos.sh`.

---

## Resumen de secuencia

1. **Fase A — COSE**: formato de mensaje agile, bajo riesgo, alto
   reaprovechamiento de lo ya construido para JOSE.
2. **Fase B — HPKE (modo clásico)**: mayor valor criptográfico real,
   verificable contra vectores oficiales del RFC.
3. **Fase B2 (opcional)** — HPKE con KEM híbrido/PQC, explícitamente
   marcado como basado en un draft, no en un RFC final.

Fuera de alcance de esta propuesta (no detallados aquí): WebAuthn/FIDO2,
Sigstore/cosign/TUF/in-toto — protocolos de ecosistema sobre primitivas, no
formatos de salida cifrada; encajarían en una propuesta separada si el
proyecto decide ampliar su ámbito más allá de laboratorio de
primitivas/formatos.
