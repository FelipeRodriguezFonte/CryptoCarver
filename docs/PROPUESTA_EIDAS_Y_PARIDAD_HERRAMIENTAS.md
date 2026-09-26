# Propuesta: eIDAS 2 / cartera europea, y paridad con la herramienta externa

### Estado de ejecución

Actualizado el 19 de septiembre de 2026, rama `feat/eidas2-eudi-wallet`.

| Fase | Estado |
|---|---|
| A — CBOR expuesto, SD-JWT VC, Token Status List | **Hecha**: núcleo con tests, nodos del Process Designer, panel propio y entradas en `OperationRegistry`. |
| B — Linter de certificados eIDAS | **Hecha**: `EidasCertificateInspector`, con TS 119 412-6 (PID, Wallet, QcPSB) y TS 119 411-8 (política WRPAC y sus requisitos). |
| B — Trusted Lists (TS 119 612) | **Hecha**: `TrustedListInspector`, sin `dss-tsl-validation`. El modelo JAXB del spec ya venía por `dss-validation`, y el módulo que falta es el que descarga y refresca por red, que es justo lo que no queremos. TS 119 602 (modelo JSON) sigue pendiente. |
| C — mdoc / mDL | **Hecha** para la mitad del emisor: `IssuerSigned`, MSO, digests, `issuerAuth`, emisión y verificación. La autenticación de dispositivo llega hasta donde puede sin sesión: se verifica un `deviceSignature` contra su session transcript; un `deviceMac` no, porque su clave sale de un ECDH contra la privada efímera del lector. |
| D — TS12 SCA e inspector OpenID4VP | **Hecha**: `Ts12ScaOperations` verifica el enlace dinámico (datos de transacción, dos factores por categoría, jti, response_mode) y `OpenId4VpInspector` lee una petición sin red. |
| E — Cierre de niveles AdES | **Parcial**: `AdesValidationOperations` valida XAdES, PAdES, CAdES y ASiC con un solo validador y emite el informe TS 119 102-2, con `dss-cades` y `dss-asic-*` añadidos. Falta la parte de **firma** en niveles LT y LTA, que necesita TSA y datos de revocación frescos, es decir red. |
| Interfaz | **Hecha**: módulo «Wallet / eIDAS» con ocho secciones (SD-JWT VC, mdoc, lista de estado, perfiles de certificado, lista de confianza, CBOR, SCA/OpenID4VP y validación AdES), en el rail y en el buscador, con un test que exige que cada frase del panel esté traducida. |
| Carril pagos — EMV ODA | **Hecha**: `EmvOdaOperations` cubre SDA, DDA y CDA en las dos mitades — recuperar y comprobar lo que presenta una tarjeta, y firmar las mismas estructuras para tener algo que comprobar. Recupera la clave del emisor (tag 90 + 92 + 9F32) y la del ICC (9F46 + 9F48 + 9F47), verifica el SSAD (93) y el SDAD (9F4B) en los dos modos, y desempaqueta el criptograma que CDA lleva dentro. Ocho nodos en el Process Designer, sección propia en el módulo EMV con un botón que personaliza una tarjeta de pruebas, y un test de traducción para el panel. |
| Carril pagos — Thales LMK de variante | **Hecha**: `ThalesLmkOperations` cifra y descifra claves bajo una LMK de variante, con la tabla de tipos de clave completa, los dos esquemas de variante (U y T), el marcado de componentes (`FF`) y la búsqueda del tipo de clave por KCV. Verificado contra el **ejemplo resuelto del manual del programador de payShield 10K, cláusula 7.2.3**, byte a byte incluido el KCV. Cinco nodos en el Process Designer y sección propia en el módulo de Claves con un botón que carga el ejemplo del manual. |
| Carril pagos — Thales Key Block | **Hecha para 3DES**: `ThalesKeyBlockOperations` lee, construye, cifra, descifra y autentica. Cabecera de 16 caracteres, tabla de usos completa con los algoritmos y modos que cada uso permite, y bloques opcionales con sus reglas. Lo que el capítulo 8 **no** dice —cómo salen de la LMK las claves de cifrado y de MAC, y si el autenticador cubre los datos claros o los cifrados— se resolvió generando un bloque con **la herramienta externa bajo la LMK de test que publica la cláusula 8.8.1** y leyendo lo que derivó: `KBEK = KBPK ⊕ 45…`, `KBAK = KBPK ⊕ 4D…` (el método de variante de X9.143 A/C), y el MAC sobre la cabecera más los datos **cifrados**. Ese vector está en los tests. La variante AES **no** está: deriva sus claves en vez de variarlas y no tenemos vector, así que se rechaza por su nombre en lugar de adivinarla. |
| Carril pagos — resto | Pendiente: Atalla AKB, Futurex MFK, SafeNet KM, ISO 8583 (**encargado a Luna**, ver [PAQUETE_LUNA_ISO8583.md](PAQUETE_LUNA_ISO8583.md)), secure messaging por esquema, MAC ISO 9797-1 algoritmos 2/4/6, PIN blocks heredados, banco de comandos host de HSM, HCE y tokenización, y el menudeo genérico. FPE se hizo por otra vía. |

Límites declarados y no disimulados: la comparación de key usage contra los
tipos A/B/C/F de la tabla 1 de EN 319 412-2 no está implementada (se informan
los bits y no se afirma conformidad), y nada de esto decide cualificación, que
la determina la lista de confianza.

## Contexto

Pregunta que motiva esta propuesta: *"¿Qué capacidades tenía la herramienta externa, o ha
incluido su sustituto capacidades nuevas que no tengamos contempladas? Y como
viene ahora la parte de eIDAS, ¿hay algo que debamos meter para las pruebas que
tengamos que hacer, incluida la cartera digital obligatoria?"*

Método: inventario del repo verificado con `grep` sobre `src/main/java` y
`pom.xml` — no de memoria ni del README — contrastado contra la documentación
publicada de la herramienta externa, sus clones web, el ARF de la cartera europea y las
especificaciones ETSI e IETF citadas al final.

Lo que la búsqueda en el código confirma, y que conviene tener presente antes de
leer nada más:

- **Cero** menciones de `SD-JWT`, `mdoc`, `18013`, `StatusList`, `qcStatement`,
  `TrustedList`/`LOTL`, `HPKE`, `BBS`. La fase B de
  [PROPUESTA_COSE_HPKE_CRIPTOAGILIDAD.md](PROPUESTA_COSE_HPKE_CRIPTOAGILIDAD.md)
  sigue sin implementar.
- **Cero** menciones de `CBOR` fuera de `COSEOperations`: la capa CBOR existe
  como dependencia transitiva de `cose-java`, pero no está expuesta.
- **DSS 6.3 ya es dependencia** (`dss-xades`, `dss-pades`, `dss-validation`,
  `dss-policy-jaxb`, `dss-service`, `dss-token`, `dss-spi`, `dss-crl-parser`).
  `PadesOperations` y `XMLSignatureOperations` ya llaman a
  `reports.getXmlValidationReport()`, es decir, **ya producimos informes de
  validación ETSI TS 119 102-2 para XAdES y PAdES** aunque no lo contemos como
  tal. No están `dss-cades`, `dss-asic-*` ni `dss-tsl-validation`.
- `AsicOperations` es ZIP artesanal (`ZipOutputStream` + manifiesto a mano) y
  sólo ASiC-S. `CMSOperations` es BouncyCastle a mano, no CAdES.
- Las únicas menciones de eIDAS en `docs/` son las cláusulas de
  [LAB_VS_PRODUCTION.md](LAB_VS_PRODUCTION.md) que dicen explícitamente que **no**
  se declara conformidad. Esa frontera no se toca en esta propuesta: lo que sigue
  produce material de prueba y evidencia de laboratorio, nunca una declaración de
  conformidad.

### Fuera de alcance deliberado

**FPE (FF1, FF3-1, FF3, FF2/VAES3, DFF).** Es un hueco real frente a la calculadora externa
—no hay nada en el repo— pero se está montando por otra vía. No se detalla aquí
para no duplicar el trabajo. Queda anotado sólo para que el inventario de huecos
esté completo.

---

# Parte 1 — La herramienta externa: estado real y sustitutos

## Qué era y en qué estado está

Un paquete freeware de Windows, congelado desde hace años, con una calculadora
criptográfica (menús genéricos, cifrado, claves, pagos, EMV y desarrollo), una
consola de comandos host para HSM de pagos, un diccionario de tags EMV y un
editor de ficheros de personalización ya retirado.

## Quién lo ha sustituido, y qué trae de nuevo

El sustituto real son las reimplementaciones web:

| Herramienta | Qué aporta |
|---|---|
| **HSM Kit** (hsmkit.com) | Clon casi 1:1 de los menús de la calculadora externa, 44 herramientas en navegador |
| **KeyLab** (keylab.cloud) | 51 herramientas, **emulación payShield 10K (37 comandos host, 111 de consola)**, PQC (ML-KEM/ML-DSA/SLH-DSA), asistente IA |
| paymentcardtools.com, emvlab.org | Piezas sueltas: key block decoder, PIN block, TLV |

Capacidades **nuevas** de los sustitutos respecto a la herramienta externa original: emulación
de comandos host de HSM, PQC y ejecución local en navegador. De las tres, ya
tenemos PQC y ejecución local. La que falta es la de comandos host.

## Huecos frente a la calculadora externa y sus clones

Verificados contra el código, ordenados por valor:

| # | Hueco | Estado verificado en el repo |
|---|---|---|
| 1 | **Formatos de protección de fabricante**: Thales LMK (esquemas y variantes) y **Thales Key Block**, Atalla **AKB**, Futurex **MFK**, SafeNet **KM**, más el *key lookup* por fuerza bruta sobre juegos de LMK de test | **Thales LMK de variante y el key lookup, hechos** (ver la tabla de estado). Faltan Thales Key Block, AKB, MFK y KM. Ojo con las fuentes: dos descripciones muy citadas del esquema de variante ponen el XOR en bytes distintos y **las dos están mal**; sólo el manual del fabricante, con su ejemplo resuelto, lo decide |
| 2 | **EMV ODA**: SDA/DDA/CDA — recuperación de CA PK, Issuer PK e ICC PK desde certificados, verificación de SSAD y SDAD | **Cubierto** por `EmvOdaOperations` (ver la tabla de estado). Lo que había antes, y sigue estando, es `EMVOperations`: UDK, session key, ARQC, ARPC y script MAC |
| 3 | **Secure messaging EMV** de Visa y Mastercard: cifrado de PIN en scripts y MAC por esquema | Parcial: hay `generateScriptMAC` genérico, faltan los perfiles de esquema |
| 4 | **ISO 8583**: bitmap y parser de mensajes; ATM NDC, Wincor, AS2805, APACS30 | Cero |
| 5 | **MAC ISO 9797-1 algoritmos 2, 4 y 6** | `MACOperations` tiene 1, 3 y 5 |
| 6 | **PIN blocks heredados**: Docutel, Diebold, Plus, ECI 1-4, Visa 1-4, Europay/Banksys y AS2805 8.1 (la herramienta externa soporta 19+) | `PinBlockFormat` cubre ISO 0/1/2/3/4, IBM 3624, ECI 1-4, Visa 1-4, Docutel, Diebold, Plus y Europay/MasterCard (Pay Now & Pay Later). Contrastado con la herramienta externa; VISA-4 estaba mal (usaba ISO-0) y está corregido. Docutel no se puede contrastar porque la herramienta lo agrupa con Diebold. AS2805 8.1 sigue pendiente. Ver `CAPTURAS_PIN_BLOCKS_HEREDADOS.md` |
| 7 | **Banco de comandos host de HSM**: payShield A0/BU/CA/CC/CI/CW/CY/DC/EC/FA/GC/HC/JA/KA/M0-M6/NC, Atalla, Futurex | **Parcial verificable**: envoltura payShield, prefijo TCP, catálogo de códigos y descompositor declarativo exacto por código/error. La forma `NC` está cargada como `PENDING_CAPTURE (NC-00)`, no como vector verificado; también el significado de error `00` queda pendiente de esa captura. Los demás cuerpos siguen opacos hasta añadir su fila respaldada por la consola HSM externa. No se abren sockets |
| 8 | **HCE y tokenización**: Visa LUK/MSD/qVSDC, Mastercard **CVC3** y PIN-CVC3, Mastercard **DS** (DSPK, DS Summary, DS Digest), ICC Dynamic Number, token **CAP**/SecureCode; **AMEX CSC v1/v2** | Sin implementación criptográfica. Campaña reproducible de capturas preparada en `CAPTURAS_CALCULADORA_HCE.md` |
| 9 | Menudeo genérico: MD4, Whirlpool, Tiger-192, variantes CRC32, **Base94**, **BCD**, tablas de **decimalización**, *bit shift*, *trace parser* (extraer hex de un tcpdump), check digit AMEX SE, **parser de ATR**, códigos de respuesta APDU, diccionario de tags EMV | Sólo CRC32 |
| — | **FPE** | Fuera de alcance de esta propuesta (ver arriba) |

## Dónde ya vamos por delante

Para no sobrevalorar la paridad: CryptoCarver ya supera a la herramienta externa y a sus clones
en TR-34, ICSF/CCA (analizador, lote y export/import con verbos nativos), PQC con
firma, XAdES/PAdES/CMS/ASiC/WS-Security/COSE, PKCS#11, Process Designer, CLI y
Batch Runner, e informes bilingües. El KCV ya cubre VISA, IBM, ATALLA, ATALLA-R,
FUTUREX, SHA256, CMAC y AES — exactamente la lista de la calculadora externa.

---

# Parte 2 — eIDAS 2 y la cartera digital obligatoria

## Calendario

Reglamento (UE) 2024/1183: **24 de diciembre de 2026**, cada Estado miembro debe
tener al menos una cartera certificada operativa y las administraciones públicas
deben aceptarla. **24 de diciembre de 2027**, la obligación de aceptación se
extiende al sector privado regulado (banca, salud, telecos, energía, transporte,
educación) y a las plataformas muy grandes.

España la denomina **Cartera IDUE**, participa en POTENTIAL y NOBID, la presentó
en el Launchpad europeo de diciembre de 2025 dentro del grupo de cabeza, con FNMT
como emisor de PID en entorno piloto e integración con Cl@ve y el DNIe.

## Marco técnico

El **ARF va por la v3.0.0 (21 de julio de 2026)**. Las especificaciones técnicas
son **TS01–TS14**:

| TS | Título |
|---|---|
| TS01 | Trust Mark de la cartera |
| TS02 | Notificación y publicación de información de proveedores |
| TS03 | **Wallet Unit Attestation (WUA)** en la emisión de PID y atestaciones |
| TS04 | **Implementación de ZKP** en la cartera |
| TS05 | Formatos y API de registro de Relying Parties |
| TS06 | Conjunto común de información de RP a registrar |
| TS07 | Interfaz de peticiones de borrado de datos a RP |
| TS08 | Interfaz de reporte de RP a autoridades de protección de datos |
| TS09 | Interacciones cartera a cartera |
| TS10 | Portabilidad y exportación de datos |
| TS11 | Catálogo de atributos y atestaciones |
| TS12 | **SCA (autenticación reforzada de cliente) con la cartera** |
| TS13 | ZK-SNARKs |
| TS14 | ZKP desde esquemas multimensaje (MMS) |

Estándares referenciados relevantes para nosotros: ISO/IEC 18013-5 y 18013-7;
**IETF RFC 9901 (SD-JWT)**, SD-JWT VC y **Token Status List**; OpenID4VCI 1.0,
OpenID4VP 1.0 y el perfil HAIP; **ETSI TS 119 412-6** (perfiles de certificado de
proveedores PID/Wallet/EAA/QEAA/PSBEAA), **TS 119 411-8** (política de
certificados de acceso de Relying Party), TS 119 475, TS 119 602, TS 119 612
(Trusted Lists), TS 119 431-1 y TS 119 432 (firma remota), EN 319 142-1 (PAdES);
**CSC API v2.2.0.0**; W3C WebAuthn L2; y los mecanismos criptográficos acordados
de ENISA.

## Qué de todo eso es construible en un laboratorio local

### Nivel A — construir ya

1. **SD-JWT / SD-JWT VC.** El más rentable. SD-JWT es **RFC 9901 desde el 19 de
   noviembre de 2025**, es el formato de credencial de la cartera, y el módulo
   JOSE con Nimbus ya es la mayor parte de la maquinaria.
2. **mdoc / mDL (ISO/IEC 18013-5).** El otro formato de credencial. Exige antes
   una capa CBOR de verdad, que además tapa el hueco que arrastra COSE.
3. **Token Status List (IETF).** Barato y necesario para probar revocación.
4. **Linter de perfiles de certificado eIDAS.** Extensión de `CertificateLinter`.
5. **Trusted Lists (TS 119 612 / TS 119 602).** DSS ya está; falta un módulo.

### Nivel B — lo que nos diferencia

6. **TS12, SCA con la cartera** — une pagos y eIDAS, que es justo nuestro cruce.
7. **Inspector de artefactos OpenID4VP/VCI**, sin red.
8. **Wallet Unit Attestation (TS03)** y atestación de cliente OAuth.
9. **Completar niveles AdES**: CAdES B-T/B-LT/B-LTA, ASiC-E, PAdES LTV, XAdES
   B-LTA, y exponer los informes TS 119 102-2 que DSS ya genera.
10. **Firma remota CSC API v2.2 / TS 119 431-1 y 432**, constructor local.
11. **Perfil criptográfico ENISA** como política en el Crypto Envelope Inspector.

### Nivel C — vigilar, no construir

ZKP (TS04, TS13 ZK-SNARKs, TS14 esquemas multimensaje tipo BBS) sigue en
movimiento y sin interoperabilidad estable. Wallet-to-wallet (TS09),
portabilidad (TS10), catálogo (TS11) y las interfaces de borrado y reporte
(TS07, TS08) son gobernanza y API, no criptografía. La Digital Credentials API
del W3C es fontanería de navegador.

---

# Fases propuestas

## Fase A — CBOR expuesto + SD-JWT VC + Token Status List

### Por qué esta primero

Las tres piezas comparten el mismo terreno —JOSE y serialización— y son
prerrequisito de casi todo lo demás. SD-JWT VC es el formato sobre el que se
apoyan TS12 y el inspector OpenID4VP; sin Status List no se puede probar
revocación; sin CBOR expuesto no hay mdoc en la fase C.

### Honestidad sobre el alcance

El MVP cubre **emisión, presentación y verificación offline** de SD-JWT VC. **No**
cubre el protocolo OpenID4VCI de emisión ni OpenID4VP de presentación: aquí se
manipulan artefactos, no se habla con nadie por red. Eso es coherente con la API
local actual, que sólo escucha en `127.0.0.1` y no acepta claves.

Para CBOR, el MVP es **inspector y notación diagnóstica** (RFC 8949), incluido el
tag 24 (`encoded-cbor`) que mdoc usa por todas partes, más conversión CBOR↔JSON.
No se toca la superficie COSE existente.

Para Token Status List, el MVP cubre **JWT y CWT de estado**, el bitstring
comprimido con DEFLATE y base64url, la consulta por índice y la verificación de
la firma. Los tamaños de estado admitidos son 1, 2, 4 y 8 bits.

### Diseño

- `crypto/CborInspector.java` — árbol, notación diagnóstica, tag 24 anidado,
  CBOR↔JSON. Reutiliza el patrón de `HexInspector` y del árbol ASN.1.
- `crypto/SdJwtOperations.java` — emisión (selección de claims divulgables, sal
  por *disclosure*, digests `_sd`, `_sd_alg`, señuelos configurables), combinación
  de presentación, verificación, y **KB-JWT** con `sd_hash`, `aud`, `nonce`,
  `jti`. Perfil SD-JWT VC encima: `vct`, `cnf`, `status`, metadatos de tipo con
  integridad `#integrity`.
- `crypto/StatusListOperations.java` — construcción, consulta y verificación.
- Controladores `ui/SdJwtController.java` y `ui/CborInspectorController.java`,
  registrados en `OperationRegistry` bajo JOSE y Generic respectivamente.
- Nodos de Process Designer: `SDJWT_ISSUE`, `SDJWT_PRESENT`, `SDJWT_VERIFY`,
  `KB_JWT_BUILD`, `STATUS_LIST_LOOKUP`, `CBOR_PARSE`. Las sales y claves de
  binding entran en `transientSecrets`, como el resto de material sensible.

### Ficheros

Nuevos: los cinco de arriba más sus FXML y entradas de `ModuleTextCatalog` en
español e inglés. Tocados: `OperationRegistry`, `docs/OPERATIONS_CATALOG.md`
(regenerado), `pom.xml`.

### Verificación

Vectores del apéndice de RFC 9901 (los ejemplos de *disclosure* y `sd_hash` están
publicados con sal fija, así que son reproducibles byte a byte). Vectores de la
especificación Token Status List. Para CBOR, los ejemplos del apéndice A de
RFC 8949. Prueba de ida y vuelta contra las credenciales de ejemplo de la
implementación de referencia de la cartera.

## Fase B — Perfiles de certificado eIDAS y Trusted Lists

### Por qué esta segunda

Máximo valor por unidad de esfuerzo: es añadir parsers de extensiones a algo que
ya existe (`CertificateLinter`, `CertificatesController`) y activar un módulo DSS
que ya está a medio camino en el `pom`.

### Honestidad sobre el alcance

El linter **describe y contrasta contra el perfil**; no emite un veredicto de
cualificación. Un certificado puede cumplir el perfil sintáctico y no ser
cualificado, porque eso lo determina la Trusted List, no los bytes.

### Diseño

- Extensión de `crypto/CertificateLinter.java` con qcStatements de EN 319 412-5
  (QcCompliance, QcSSCD, QcType esign/eseal/web, QcRetentionPeriod, QcPDS,
  QcLimitValue, OIDs de semántica), roles PSD2 de **TS 119 495** (PSP_AS, PSP_PI,
  PSP_AI, PSP_IC, nombre e identificador de la NCA), y los dos perfiles nuevos:
  **TS 119 412-6** para proveedores PID, Wallet, EAA, QEAA y PSBEAA, y
  **TS 119 411-8** para certificados de acceso de Relying Party.
- `crypto/TrustedListOperations.java` sobre `dss-tsl-validation`: parseo de LOTL
  y TL, verificación de la firma XML, navegación de servicios, estados y
  cualificadores, y consulta "¿está este certificado en esta lista, y con qué
  estado en qué fecha?". Añadir también el modelo de datos JSON de TS 119 602.

### Ficheros

Tocados: `CertificateLinter`, `CertificatesController`, `KeyCertificateFormatService`.
Nuevos: `TrustedListOperations` y su controlador. `pom.xml`: `dss-tsl-validation`.

### Verificación

Certificados cualificados reales de la LOTL europea en modo lectura, y
certificados de laboratorio generados con el propio `CertificateGenerator` con
qcStatements sintéticos para cubrir las ramas de error. La LOTL se carga desde
fichero local; **no** se descarga en tiempo de prueba.

## Fase C — mdoc / mDL (ISO/IEC 18013-5)

### Honestidad sobre el alcance

El MVP es **estructural y de verificación offline**: `IssuerSigned`,
`MobileSecurityObject` con `ValueDigests` y `DeviceKeyInfo`, `IssuerSignedItem`
con `digestID`, `random` y `elementValue`, `DeviceSigned`, y `DeviceAuth` en sus
dos formas (`DeviceSignature` como COSE_Sign1 y `DeviceMac` como COSE_Mac0),
sobre el `SessionTranscript` correspondiente. **No** cubre el *device engagement*
por NFC o BLE ni el cifrado de sesión: eso es transporte, no criptografía de
laboratorio. ISO 18013-7 (presentación remota) queda para después.

### Diseño

`crypto/MdocOperations.java` sobre la capa CBOR de la fase A y el
`COSEOperations` existente. Espacio de nombres `org.iso.18013.5.1` con el
catálogo de elementos, y validación de que cada digest del MSO case con su
`IssuerSignedItem`.

### Verificación

Vectores del anexo D de ISO/IEC 18013-5 y credenciales de ejemplo de la
implementación de referencia de la cartera.

## Fase D — TS12: SCA con la cartera, e inspector OpenID4VP

### Por qué vale la pena

Es la pieza que nadie tiene en un laboratorio de escritorio, y es exactamente
nuestro cruce: enlace dinámico PSD2 resuelto con criptografía de credenciales.

### Diseño

- Verificador de **enlace dinámico**: credencial SD-JWT VC con
  `category: urn:eu:europa:ec:eudi:sua:sca`, `cnf` con la clave de binding, y
  KB-JWT que debe contener `transaction_data_hashes` (SHA-256 del payload),
  `sd_hash`, `nonce`, `aud`, `jti` aleatorio y `amr` con **al menos dos**
  categorías de factor (conocimiento, posesión, inherencia). Los cuatro tipos:
  `urn:eudi:sca:payment:1`, `urn:eudi:sca:login_risk_transaction:1`,
  `urn:eudi:sca:account_access:1`, `urn:eudi:sca:emandate:1`, cada uno validado
  contra su JSON Schema.
- Inspector de artefactos OpenID4VP sin red: request objects JAR, `client_id`
  incluido el esquema `x509_san_dns`, consultas DCQL, `vp_token`, y cifrado de
  respuesta (ECDH-ES + A256GCM).

### Verificación

Comprobación de que el verificador **rechaza** un KB-JWT cuyo
`transaction_data_hashes` no corresponde al importe y beneficiario presentados —
que es la prueba que de verdad importa— y que rechaza un `amr` con un solo factor.

## Fase E — Cierre de niveles AdES

`dss-cades` y `dss-asic-cades`/`dss-asic-xades` para CAdES B-T/B-LT/B-LTA y
ASiC-E, sustituyendo el ZIP artesanal de `AsicOperations`; PAdES LTV con
diccionario DSS y sellos de documento; XAdES B-LTA. Y exponer como tal los
**informes de validación TS 119 102-2** que ya se generan en `PadesOperations` y
`XMLSignatureOperations`, extendiéndolos a CAdES y ASiC. Esto convierte la salida
de la app en evidencia presentable, sin declarar conformidad.

---

# Dependencias

| Necesidad | Opción | Comprobar antes |
|---|---|---|
| CBOR expuesto | `com.upokecenter:cbor` o `jackson-dataformat-cbor` | Cuál usa ya `cose-java` de forma transitiva, para no duplicar |
| SD-JWT | Nimbus 9.37.3 **no** lo trae | O se implementa a mano sobre Nimbus, o se evalúa `com.authlete:sd-jwt`. Verificar versión en Maven Central antes de fijarla |
| Trusted Lists | `dss-tsl-validation` 6.3 | Misma versión que el resto de DSS |
| CAdES y ASiC | `dss-cades`, `dss-asic-cades`, `dss-asic-xades` 6.3 | Impacto en el tamaño del *fat jar* |

Aplica el criterio ya establecido en
[PROPUESTA_COSE_HPKE_CRIPTOAGILIDAD.md](PROPUESTA_COSE_HPKE_CRIPTOAGILIDAD.md):
verificar publicación y versión en Maven Central antes de fijarla, no asumirla.

# Límite laboratorio / producción

Ninguna fase de esta propuesta modifica lo que dice
[LAB_VS_PRODUCTION.md](LAB_VS_PRODUCTION.md). En concreto:

- No se declara conformidad eIDAS, ni cualificación de certificados, ni
  certificación de cartera. El linter describe; la Trusted List informa.
- Las credenciales, sales, claves de binding y KB-JWT son material sensible:
  `transientSecrets` en Process Designer, y perfil de traza `MASKED` por defecto
  en cualquier informe que los incluya.
- Las listas de confianza se cargan desde fichero local. La app no descarga
  LOTL, JWKS, metadatos de emisor ni Status Lists por su cuenta.

# Resumen de secuencia

Dos carriles en paralelo, porque tocan zonas distintas del código:

**Carril eIDAS** — Fase A (CBOR + SD-JWT VC + Status List) → Fase B (linter de
certificados + Trusted Lists) → Fase C (mdoc/mDL) → Fase D (TS12 + OpenID4VP) →
Fase E (cierre AdES).

**Carril pagos** — ~~EMV ODA (SDA/DDA/CDA)~~ hecha → formatos
Thales/Atalla/Futurex → ISO 8583 → secure messaging por esquema → el menudeo
genérico. FPE va por separado, fuera de esta propuesta.

## Vectores externos pendientes para cerrar los huecos 3, 7 y 8

El banco payShield implementa únicamente el framing verificable con el
*payShield 10K Host Programmer's Manual*, documento 007-001518-023, revisión A1,
secciones 1.3–1.5, y la forma de una respuesta `NC` suministrada. La procedencia
original de esa respuesta no quedó registrada, de modo que no se presenta como
KAT externo. La receta exacta para sustituirla y capturar los siguientes
comandos está en `docs/CAPTURAS_CONSOLA_HSM_PAYSHIELD.md`. En todos los casos
se necesitan petición, respuesta, cabecera, prefijo TCP, ventana completa y
versión de la herramienta:

- A0, A6, A8 y BU: un caso de LMK de variante y otro de Key Block LMK, con los
  campos opcionales visibles y la respuesta de error producida al alterar un
  campo obligatorio.
- CA, CC, DC y EC: PAN sintético, PIN sintético, formatos de bloque, claves de
  test y bloques de entrada/salida. Un segundo caso debe cambiar un dígito no
  relacionado con paridad DES.
- CW y CY: PAN sintético, expiración, código de servicio, CVK de test, CVV y una
  verificación fallida.
- FA: ZMK/ZPK de test, esquemas de clave de entrada/salida y KCV devuelto.
- M0, M2, M4 y M6: algoritmo/modo, IV, relleno, clave de test, datos exactos y
  MAC/cifrado resultante, incluyendo un caso en el límite de bloque.

Para **secure messaging EMV**, la campaña reproducible está en
`docs/CAPTURAS_CALCULADORA_SECURE_MESSAGING.md`: fichas separadas
para Visa CSK y Mastercard SKD con versión/perfil exactos, MK-SMI/MK-SMC de
test, PAN/PSN, ATC, APDU completa, datos claros, entrada exacta al MAC, clave de
sesión derivada, bloque rellenado, PIN cifrado y MAC final. Incluye un segundo
caso que cambia sólo la cabecera APDU para demostrar qué bytes autentica el
perfil.

Para **HCE/tokenización**, la campaña reproducible está en
`docs/CAPTURAS_CALCULADORA_HCE.md`: fichas independientes para Visa
LUK/MSD/qVSDC, Mastercard CVC3/PIN-CVC3 y DS (DSPK, DS Summary, DS Digest), ICC
Dynamic Number, CAP/SecureCode y AMEX CSC v1/v2, más pares que cambian sólo UN,
ATC, contador o dato. Hasta disponer de esos vectores no se deducen algoritmos
a partir del nombre comercial.

Nota sobre el ODA ya hecho: las dos operaciones que firman certificados
producen tres objetos de datos cada una (el certificado, el resto de la clave
pública y el exponente), y un nodo del Process Designer emite un solo
`FlowValue`, así que viven en el banco del módulo EMV y no como nodos. Todo lo
que da un valor único — los datos estáticos, las dos firmas y los informes de
verificación — sí es nodo.

Empezar por la fase A: es lo más cercano a lo que ya funciona, RFC 9901 es
estable, y es el cimiento de las fases C y D.

# Referencias

- [HSM Kit](https://hsmkit.com/) · [KeyLab](https://keylab.cloud/)
- [ARF v3.0.0](https://eudi.dev/latest/) · [Especificaciones técnicas TS01–TS14](https://eudi.dev/latest/technical-specifications/)
- [TS12 — SCA con la cartera](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md)
- [RFC 9901 — Selective Disclosure for JWTs](https://www.rfc-editor.org/rfc/rfc9901.html)
- [ETSI TS 119 411-8 V1.1.1 (2025-10)](https://www.etsi.org/deliver/etsi_ts/119400_119499/11941108/01.01.01_60/ts_11941108v010101p.pdf) · [ETSI TS 119 475 V1.2.1 (2026-03)](https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf)
- ETSI TS 119 412-6 V1.2.1 (2026-04), perfiles de certificado para proveedores PID, Wallet, EAA, QEAA y PSBEAA
- [Plazo de diciembre de 2026 (EADTrust)](https://www.eadtrust.eu/en/blog/december-2026-deadline-eudi-wallet/) · [Estado de implantación UE-27](https://b2trust.com/en/blog/eidas-2-implementation-status-eu-27-q2-2026)

---

*Documento de propuesta. Fecha de investigación: 18 de septiembre de 2026.
ARF v3.0.0. Inventario del repo verificado sobre la rama
`feat/icsf-parity-with-python`, versión 2.4.0.*
