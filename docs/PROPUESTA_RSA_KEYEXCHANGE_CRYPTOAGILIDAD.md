# Propuesta — Intercambio de claves con RSA y contenedor cripto-ágil

**Fecha:** 2026-08-07
**Alcance:** dos capacidades nuevas solicitadas por el propietario del producto.
1. Importación/exportación de claves simétricas con un método distinto de TR-31 o AES Key Wrap.
2. Un formato de dato cifrado autodescriptivo (metadatos + ciphertext) que facilite la cripto-agilidad (cambio de algoritmo/clave sin romper compatibilidad).

Este documento sigue la convención de `docs/HANDOFF_*.md`: diagnóstico → diseño → dónde tocar el código → fases → verificación.

---

## 1. Diagnóstico: qué existe ya y qué falta

| Pieza | Dónde | Cubre |
|---|---|---|
| `KeyWrapOperations.java` | `crypto/` | AES Key Wrap RFC 3394 / RFC 5649 — **simétrico**, KEK↔clave |
| `TR31Operations.java` + `TR31.java` | `crypto/` | Bloques de clave TR-31 (headers A/B/C/D) — **simétrico** |
| `CMSOperations.generateEnvelopedData/decryptEnvelopedData` | `crypto/` | CMS EnvelopedData con `JceKeyTransRecipientInfoGenerator` → **ya es transporte RSA de una clave**, pero expuesto solo como "cifra datos arbitrarios", sin metadatos de clave (KCV, uso, versión) |
| JWE `alg=RSA-OAEP-256/512` | `JOSEController` / `JOSEService` | JOSE ya soporta cifrar un payload bajo una clave pública RSA (y también `ECDH-ES`) — de nuevo, genérico, no enmarcado como "exportar una clave" |
| `KeyCertificateFormatService` | `service/` | PKCS#8 / PKCS#12 / JWK — protección de la **clave privada con contraseña**, no intercambio de una clave simétrica entre dos partes |
| PKCS#11 (`Pkcs11Session`, `JnaPkcs11NativeBridge`) | `crypto/hsm/` | Inventario de mecanismos (`CKM_RSA_PKCS_OAEP` aparece solo como *nombre* en `Pkcs11NativeConstants`) — **no hay `C_WrapKey`/`C_UnwrapKey` implementado** |
| `FileCipherRecipe` / `FileCipherRecipeCodec` | `model/` | Metadatos versionados (algoritmo, IV, AAD) **junto al cifrado de un fichero** — es el precedente más cercano a un "envelope", pero es un fichero de configuración *aparte*, no viaja pegado al ciphertext, y no tiene `keyId`/`keyVersion` |

**Conclusión del diagnóstico:** la app ya tiene todas las primitivas RSA necesarias (CMS EnvelopedData, JWE RSA-OAEP) y ya tiene el hábito de diseño de "metadatos versionados junto al resultado" (`FileCipherRecipe`). Lo que falta no es criptografía nueva, sino **empaquetar** esas primitivas como herramientas de primera clase con la semántica correcta (intercambio de claves, no "cifrar un texto") y **generalizar** el patrón de recipe a un contenedor reutilizable en toda la app.

---

## 2. Parte 1 — Intercambio de claves simétricas con RSA

### 2.1 Estándares candidatos (de más a menos recomendado para este proyecto)

**A. TR-34 (ASC X9 TR-34-2019) — recomendado como objetivo principal**
Es el hermano de TR-31 pensado exactamente para esto: enviar una clave simétrica (típicamente la primerísima KEK de un terminal/HSM, cuando aún no hay ningún canal simétrico establecido) usando criptografía asimétrica. Estructura: el emisor firma (CMS SignedData, con su certificado de firma) y luego cifra (CMS EnvelopedData, `KeyTransRecipientInfo` con RSA-OAEP y el certificado RSA del receptor) un bloque de clave con forma TR-31. Incluye un protocolo de *binding* de dos pasos (el receptor manda su clave pública + un nonce; el emisor responde con la clave cifrada y firmada, atada a ese nonce) para evitar repetición.
- **Por qué encaja aquí:** ya tenemos las tres piezas — `CMSOperations.generateSignedData`, `CMSOperations.generateEnvelopedData/decryptEnvelopedData` y el formateador de bloques `TR31.java`. TR-34 es, en gran medida, componer piezas que ya existen.
- Es el estándar real que usan HSMs de pago (Thales, Futurex, etc.) para *remote key loading*.

**B. Envoltura RSA-OAEP genérica (sin nombre de estándar de pagos, pero es el mecanismo universal)**
`RSAES-OAEP` (RFC 8017 / PKCS#1 v2.2) tal cual, expuesto como operación de primera clase: "envuelve esta clave simétrica bajo esta clave pública RSA" → produce bytes envueltos + metadatos (KCV, algoritmo, tamaño). Tres perfiles de salida posibles, todos reutilizando código ya existente:
  - bytes OAEP crudos (nuevo, pequeño)
  - JWE compacto (`RSAEncrypter`/`RSADecrypter` — ya existen en `JOSEService`)
  - CMS EnvelopedData (`CMSOperations` — ya existe)
- **Por qué importa igual:** cubre el caso *no bancario* (intercambiar una clave AES entre dos sistemas de software cualquiera) sin la ceremonia de TR-34, que solo tiene sentido en el mundo de pagos.

**C. Wrap/Unwrap nativo por PKCS#11 (`CKM_RSA_PKCS_OAEP`)**
Para claves que viven dentro de un HSM: envolver un *key handle* bajo una clave pública RSA sin que el material salga nunca del HSM, y la operación inversa (`C_UnwrapKey`) para materializar la clave recibida directamente como objeto no exportable del HSM. Es la versión "de producción real" de la opción B (que necesariamente expone bytes de clave en memoria de la JVM en algún punto).

**D. ASC X9.143:2022 (mención, no para ahora)**
Estándar unificado que fusiona TR-31 y TR-34 bajo un único esquema de *Key Block* (añade tipos de bloque asimétricos a los ya simétricos). Adopción todavía incipiente en la industria. Lo dejo anotado para que el diseño de metadatos (sección 3) no cierre la puerta a un futuro perfil X9.143, pero no lo propongo como entregable ahora.

No propongo ECIES/ECDH como bloque nuevo — `ECDH-ES` ya existe dentro de JWE; basta con referenciarlo desde la nueva pantalla en vez de duplicarlo.

### 2.2 Dónde tocar el código

- `crypto/RsaKeyWrapOperations.java` **(nuevo)** — opción B, paralelo a `KeyWrapOperations.java`: `wrapOaep(pubKey, keyData, profile)` / `unwrapOaep(privKey, wrapped, profile)`, donde `profile` ∈ `{RAW, JWE_COMPACT, CMS_ENVELOPED}`, delegando en `JOSEService`/`CMSOperations` para los dos últimos.
- `crypto/TR34Operations.java` **(nuevo)** — opción A, construido sobre `CMSOperations` + `TR31.java`. Empezar solo con el perfil *one-pass* (sin el nonce de binding) para MVP; el *two-pass* completo es una iteración posterior si hace falta interoperar con HSMs reales.
- `crypto/hsm/Pkcs11Session.java` / `JnaPkcs11NativeBridge.java` — añadir `wrapKey`/`unwrapKey` para `CKM_RSA_PKCS_OAEP` (Fase C, después de A y B).
- UI: nueva pestaña/`TitledPane` **"RSA Key Exchange"** dentro de `keys.fxml`, justo al lado de "TR-31 Key Blocks" (mismo `KeysController.java`, mismo grupo "Symmetric" en `SidePanel.buildKeysTree()` — el usuario que ya busca ahí "cómo saco esta clave" encuentra la alternativa RSA sin cambiar de sección).
- Registrar rutas en `UiNavigationRegistry.java` (`Module.KEYS_SYMMETRIC`, sección `"RSA Key Exchange"`) y en `OperationRegistry` (nuevas entradas, categoría `Keys`, seguirán apareciendo solas en `docs/OPERATIONS_CATALOG.md` al regenerarse).

---

## 3. Parte 2 — Contenedor cripto-ágil

### 3.1 Objetivo

Que cualquier resultado cifrado de la app pueda "envolverse" con una cabecera compacta que permita, más adelante, saber **con qué clave y qué algoritmo se cifró** sin tener que preguntárselo a nadie — y que un cambio de algoritmo (ej. migrar de AES-CBC a AES-GCM, o rotar una KEK) no rompa la capacidad de leer lo cifrado con la versión anterior.

### 3.2 Diseño: generalizar `FileCipherRecipe` en `CryptoEnvelope`

Nuevo modelo compartido `model/CryptoEnvelope.java`, con estos campos (superconjunto de lo que ya tiene `FileCipherRecipe`):

| Campo | Para qué sirve |
|---|---|
| `envVersion` | versión del esquema del envelope en sí (`"CCE-1"`) — permite evolucionar el propio formato |
| `alg` | identificador canónico del algoritmo (reutilizo nombres estilo JOSE: `A256GCM`, `AES-256-CBC`, `RSA-OAEP-256`...) para que sea reconocible fuera de esta app |
| `kid` | identificador de clave — **el campo que de verdad habilita agilidad**: sin esto, rotar una clave obliga a adivinar cuál se usó |
| `keyVersion` | entero incremental, complementa `kid` en escenarios de rotación de KEK (mismo espíritu que el número de versión de clave de TR-31) |
| `kcv` | *key check value* de la clave usada — permite comprobar *antes* de descifrar si tienes la clave correcta, en vez de fallar con un tag de autenticación inválido y no saber por qué |
| `createdAt` | ISO-8601 — soporta políticas "rota si tiene más de N días" |
| `ivNonceHex`, `aadHex` | ya existen en `FileCipherRecipe`, se heredan tal cual |
| `extensions` | mapa abierto para metadatos específicos de la operación (p. ej. parámetros KDF si la clave se derivó, o el algoritmo de la KEK si esto es *envelope encryption* de dos niveles al estilo AWS KMS/Google Tink) |

`FileCipherRecipe` pasa a ser una vista/adaptador delgado sobre `CryptoEnvelope` (o se migra con una función de compatibilidad en `FileCipherRecipeCodec` que lee recipes v1.0 antiguos y los sube a `CryptoEnvelope`) — sin romper `.ccconfig` ya guardados por usuarios.

### 3.3 Dos formatos de serialización (elección de UX, no de arquitectura)

- **Compacto**: `CCE1.<base64url(header-json)>.<base64url(ciphertext)>` — mismo esqueleto mental que JWE compacto (que los usuarios de esta app ya conocen), fácil de copiar/pegar en un campo de texto.
- **JSON**: `{ "cce": 1, "alg": ..., "kid": ..., "kcv": ..., ..., "ciphertext": "<b64>" }` — mejor si el consumidor final es otro sistema que ya habla JSON.

### 3.4 Dónde engancha en la UX (esto es lo importante)

En vez de una pantalla aislada que nadie visita, propongo dos puntos de entrada:

1. **Acción transversal en la barra de resultado.** Junto a los botones ya existentes `Add to Shelf` / `Copy Output` (en `ResultAreaTracker.java` / `ModernMainController.java`) añadir **"Wrap as Envelope"** — disponible después de *cualquier* operación de cifrado, no solo una. Es la decisión de UX de mayor apalancamiento: el usuario no tiene que ir a buscar la funcionalidad, la encuentra justo donde ya está mirando el resultado que quiere hacer agilizable.
2. **Inspector de solo lectura**, nuevo módulo bajo Utilities: **"Crypto Envelope Inspector"** — pega/arrastra un envelope, ves la cabecera decodificada (algoritmo, `kid`/versión, antigüedad, si el `kcv` coincide con alguna clave que tengas en el Key Lab de esta sesión) y lo desenvuelves si tienes la clave. Sigue exactamente el patrón ya establecido por `CmsInspectorController` / el decodificador ASN.1 / el EMV TLV Inspector — no introduce un patrón de UX nuevo, reutiliza uno que el usuario de esta app ya conoce.

### 3.5 Dónde tocar el código

- `model/CryptoEnvelope.java` + `model/CryptoEnvelopeCodec.java` (compacto y JSON) — paralelo exacto a `FileCipherRecipe`/`FileCipherRecipeCodec`.
- `crypto/CryptoEnvelopeInspector.java` — paralelo a `CmsInspector.java` (parseo + reporte de solo lectura).
- `ui/CryptoEnvelopeInspectorController.java` + nuevo `fxml` — paralelo a `CmsInspectorController`.
- `ui/ResultAreaTracker.java` / `ModernMainController.java` — nuevo `handleWrapAsCryptoEnvelope()`, cableado igual que `handleAddToShelf`.
- `UiNavigationRegistry` (`Module.GENERIC`, sección `"Crypto Envelope Inspector"`) + `OperationRegistry`.

---

## 4. Fases propuestas

| Fase | Contenido | Esfuerzo | Riesgo | Por qué en ese orden |
|---|---|---|---|---|
| **A** | `RsaKeyWrapOperations` (3 perfiles) + `CryptoEnvelope`/`CryptoEnvelopeCodec` + acción "Wrap as Envelope" + Inspector | Medio | Bajo | Responde a **ambas** peticiones ya en software puro, reutilizando primitivas existentes (CMS, JWE); solo añade ficheros y entradas de registro — mismo patrón de bajo riesgo usado en todo el histórico de `add(routes, ...)` |
| **B** | `TR34Operations` (perfil *one-pass*) integrado junto a TR-31 en Keys | Medio-alto | Medio | Es el estándar "de verdad" del mundo de pagos; tiene más valor para el segmento objetivo de la app, pero depende de A estar ya probado (reutiliza `CMSOperations`) |
| **C** | Wrap/Unwrap nativo PKCS#11 (`CKM_RSA_PKCS_OAEP`) + perfil TR-34 *two-pass* + explorar ASC X9.143 | Alto | Medio-alto | Toca código nativo (JNA) y HSM real — solo tiene sentido una vez A y B estén asentados y haya un caso de uso concreto que lo pida |

---

## 5. Verificación prevista (siguiendo el patrón ya usado en el repo)

- Vectores conocidos: TR-34 no tiene vectores públicos tan extendidos como TR-31, pero ANSI X9 publica ejemplos de referencia en el propio estándar; para la Fase A, RSA-OAEP y CMS EnvelopedData ya están cubiertos por primitivas de BouncyCastle con tests propios existentes (`CMSOperationsTest`, `CMSOperationsTest`).
- Test de round-trip: envolver con `CryptoEnvelope`, desenvolver, comparar con el original — igual que ya hace `FileCipherRecipeCodecTest`.
- Test de migración: un `.ccconfig` v1.0 (`FileCipherRecipe`) antiguo se sigue leyendo tras generalizar a `CryptoEnvelope`.
