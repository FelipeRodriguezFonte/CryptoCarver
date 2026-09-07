# Process Designer Operation Reference (Ola 5B.1–5B.2b)

This document provides reference documentation for the Process Designer node
operations introduced in **Ola 5B.1–5B.2b**.

---

## 1. Summary of Operations

| Type | Family | Wrapped Facade | Inputs | Output | Sensitive Params |
|---|---|---|---|---|---|
| `CONCAT` | Plumbing | (Buffer concatenation) | `a`, `b` (Any) | Binary | None |
| `SLICE` | Plumbing | `java.util.Arrays.copyOfRange` | `input` (Any) | Binary | None |
| `PAD` | Plumbing | `PaddingUtil.addPadding` | `input` (Any) | Binary | None |
| `UNPAD` | Plumbing | `PaddingUtil.removePadding` | `input` (Any) | Binary | None |
| `XOR` | Plumbing | `KeyOperations.xor` | `a`, `b` (Any) | Binary | None |
| `ASSERT_EQUALS` | Plumbing | `MACOperations.constantTimeEquals` | `actual`, `expected` (Any) | Input (`actual`) | None |
| `BASE32_ENCODE` | Conversions | `CodecRegistry.encode("BASE32", ...)` | `input` (Any) | Binary | None |
| `BASE32_DECODE` | Conversions | `CodecRegistry.decode("BASE32", ...)` | `input` (Text / Base32) | Binary | None |
| `BASE58_ENCODE` | Conversions | `CodecRegistry.encode("BASE58", ...)` | `input` (Any) | Binary | None |
| `BASE58_DECODE` | Conversions | `CodecRegistry.decode("BASE58", ...)` | `input` (Text / Base58) | Binary | None |
| `BASE58CHECK_ENCODE` | Conversions | `CodecRegistry.encode("BASE58CHECK", ...)` | `input` (Any) | Binary | None |
| `BASE58CHECK_DECODE` | Conversions | `CodecRegistry.decode("BASE58CHECK", ...)` | `input` (Text / Base58Check) | Binary | None |
| `EBCDIC_ENCODE` | Conversions | `EBCDICConverter.textToEbcdic` | `input` (Text UTF-8) | EBCDIC | None |
| `EBCDIC_DECODE` | Conversions | `EBCDICConverter.ebcdicToText` | `input` (EBCDIC) | Text UTF-8 | None |
| `COMPRESS` | Conversions | `CompressionCodec.compress` | `input` (Any) | Binary | None |
| `DECOMPRESS` | Conversions | `CompressionCodec.decompress` | `input` (Any) | Binary | None |
| `CHARSET_CONVERT` | Conversions | `java.nio.charset.Charset` | `input` (Text UTF-8) | Text UTF-8 | None |
| `ASN1_DECODE` | Utilities | `ASN1Parser.parse` | `input` (Binary) | Text UTF-8 | None |
| `CHECK_DIGIT_CALC` | Utilities | `CheckDigitCalculator.calculate` | `input` (Text UTF-8) | Text UTF-8 | None |
| `CHECK_DIGIT_VERIFY` | Utilities | `CheckDigitCalculator.verify` | `input` (Text UTF-8) | Text UTF-8 | None |
| `MODULAR_ARITHMETIC` | Utilities | `ModularArithmetic.compute` | `a`, `b`, `modulus` (Text/Hex/Dec) | Text UTF-8 | None |
| `UUID_GENERATE` | Utilities | `UUIDGenerator.generate` | (None / Generator) | Text UTF-8 | None |
| `BYTE_STATISTICS` | Utilities | `ByteStatistics.analyze` | `input` (Any) | Text UTF-8 | None |

---

## 2. Plumbing Operations

### `CONCAT`
- **Category**: Plumbing
- **Description**: Concatenates two byte streams into one.
- **Ports**:
  - `a`: Required, accepts all representations.
  - `b`: Required, accepts all representations.
  - Output: `BINARY` representation containing `a || b`.
- **Parameters**: None.
- **Facade**: Native byte buffer concatenation.
- **Limitations**: In-memory byte array concatenation.

### `SLICE`
- **Category**: Plumbing
- **Description**: Extracts a slice from a byte sequence using offset and length.
- **Ports**:
  - `input`: Required, accepts all representations.
  - Output: `BINARY` representation.
- **Parameters**:
  - `offset`: Number (default `0`).
  - `length`: Number (default `-1`, representing slice to end).
- **Facade**: `java.util.Arrays.copyOfRange`.
- **Limitations**: Negative offset or requesting length exceeding available bytes throws `IllegalArgumentException`.

### `PAD`
- **Category**: Plumbing
- **Description**: Applies cryptographic padding to an input byte sequence.
- **Ports**:
  - `input`: Required, accepts all representations.
  - Output: `BINARY` representation.
- **Parameters**:
  - `paddingType`: Combo (`PKCS7`, `ISO7816_4`, `ANSI_X923`, `ZERO_BYTE`). Default `PKCS7`.
  - `blockSize`: Number (default `16`).
- **Facade**: `com.cryptocarver.crypto.PaddingUtil.addPadding`.

### `UNPAD`
- **Category**: Plumbing
- **Description**: Strips cryptographic padding from an input byte sequence.
- **Ports**:
  - `input`: Required, accepts all representations.
  - Output: `BINARY` representation.
- **Parameters**:
  - `paddingType`: Combo (`PKCS7`, `ISO7816_4`, `ANSI_X923`, `ZERO_BYTE`). Default `PKCS7`.
- **Facade**: `com.cryptocarver.crypto.PaddingUtil.removePadding`.
- **Limitations**: Invalid padding triggers safe error without leaking data.

### `XOR`
- **Category**: Plumbing
- **Description**: Bitwise XOR of two byte arrays of identical length.
- **Ports**:
  - `a`: Required, accepts all representations.
  - `b`: Required, accepts all representations.
  - Output: `BINARY` representation.
- **Parameters**: None.
- **Facade**: `com.cryptocarver.crypto.KeyOperations.xor(byte[], byte[])`.
- **Limitations**: Inputs must have identical byte lengths.

### `ASSERT_EQUALS`
- **Category**: Plumbing
- **Description**: Verifies that two inputs are identical using constant-time comparison. Fails workflow execution if they differ.
- **Ports**:
  - `actual`: Required, accepts all representations.
  - `expected`: Required, accepts all representations.
  - Output: Forwards `actual` representation and bytes.
- **Parameters**: None.
- **Facade**: `com.cryptocarver.crypto.MACOperations.constantTimeEquals(byte[], byte[])`.
- **Limitations**: Safe rejection throws `IllegalStateException` showing only length difference, zero sensitive bytes leaked.

---

## 3. Conversion Operations

### `BASE32_ENCODE` / `BASE32_DECODE`
- **Category**: Conversions
- **Description**: Encodes binary to RFC 4648 Base32, or decodes Base32 string to binary.
- **Ports**:
  - `BASE32_ENCODE`: Input accepts all representations; output is `BINARY` (ASCII text).
  - `BASE32_DECODE`: Input accepts `TEXT_UTF8`, `BINARY`; output is `BINARY`.
- **Facade**: `com.cryptocarver.crypto.CodecRegistry.encode("BASE32", ...)` / `decode`.

### `BASE58_ENCODE` / `BASE58_DECODE`
- **Category**: Conversions
- **Description**: Encodes binary to Bitcoin Base58, or decodes Base58 string to binary.
- **Ports**:
  - `BASE58_ENCODE`: Input accepts all representations; output is `BINARY` (ASCII text).
  - `BASE58_DECODE`: Input accepts `TEXT_UTF8`, `BINARY`; output is `BINARY`.
- **Facade**: `com.cryptocarver.crypto.CodecRegistry.encode("BASE58", ...)` / `decode`.

### `BASE58CHECK_ENCODE` / `BASE58CHECK_DECODE`
- **Category**: Conversions
- **Description**: Encodes binary with double-SHA256 checksum to Base58Check, or decodes and validates checksum.
- **Ports**:
  - `BASE58CHECK_ENCODE`: Input accepts all representations; output is `BINARY`.
  - `BASE58CHECK_DECODE`: Input accepts `TEXT_UTF8`, `BINARY`; output is `BINARY`.
- **Facade**: `com.cryptocarver.crypto.CodecRegistry.encode("BASE58CHECK", ...)` / `decode`.

### `EBCDIC_ENCODE` / `EBCDIC_DECODE`
- **Category**: Conversions
- **Description**: Converts text to EBCDIC byte encoding (CP037/IBM037) or EBCDIC bytes to UTF-8 text.
- **Ports**:
  - `EBCDIC_ENCODE`: Input accepts `TEXT_UTF8`; output is `EBCDIC`.
  - `EBCDIC_DECODE`: Input accepts `EBCDIC`; output is `TEXT_UTF8`.
- **Parameters**:
  - `codepage`: Text (default `CP037`).
- **Facade**: `com.cryptocarver.crypto.EBCDICConverter.textToEbcdic` / `ebcdicToText`.

### `COMPRESS` / `DECOMPRESS`
- **Category**: Conversions
- **Description**: Compresses or decompresses binary payloads.
- **Ports**:
  - `input`: Required, accepts all representations.
  - Output: `BINARY` representation.
- **Parameters**:
  - `format`: Combo (`GZIP`, `DEFLATE`, `ZLIB`). Default `GZIP`.
- **Facade**: `com.cryptocarver.crypto.CompressionCodec.compress` / `decompress`.

### `CHARSET_CONVERT`
- **Category**: Conversions
- **Description**: Re-encodes text from source charset to target charset.
- **Ports**:
  - `input`: Required, accepts `TEXT_UTF8`.
  - Output: `TEXT_UTF8`.
- **Parameters**:
  - `sourceCharset`: Combo (`UTF-8`, `ISO-8859-1`, `US-ASCII`, `UTF-16`, `Windows-1252`). Default `UTF-8`.
  - `targetCharset`: Combo (`UTF-8`, `ISO-8859-1`, `US-ASCII`, `UTF-16`, `Windows-1252`). Default `ISO-8859-1`.
- **Facade**: `java.nio.charset.Charset`.

---

## 4. Utility & Inspection Operations

### `ASN1_DECODE`
- **Category**: Utilities
- **Description**: Parses DER/BER ASN.1 structures into a human-readable structural tree dump.
- **Ports**:
  - `input`: Required, accepts `BINARY`.
  - Output: `TEXT_UTF8` (tree representation).
- **Parameters**: None.
- **Facade**: `com.cryptocarver.crypto.ASN1Parser.parse`.

### `CHECK_DIGIT_CALC` / `CHECK_DIGIT_VERIFY`
- **Category**: Utilities
- **Description**: Calculates or verifies check digits (Luhn, Mod10, Verhoeff, Damm, ISO 7064).
- **Ports**:
  - `input`: Required, accepts `TEXT_UTF8`.
  - Output: `TEXT_UTF8` (calculated digit or verification boolean string).
- **Parameters**:
  - `algorithm`: Combo (`LUHN`, `MOD10`, `VERHOEFF`, `DAMM`, `ISO7064_MOD11_2`, `ISO7064_MOD97_10`). Default `LUHN`.
- **Facade**: `com.cryptocarver.crypto.CheckDigitCalculator.calculate` / `verify`.

### `MODULAR_ARITHMETIC`
- **Category**: Utilities
- **Description**: Calculates modular arithmetic: `(A OP B) mod N` or `(A^B) mod N`.
- **Ports**:
  - `a`: Required, accepts `TEXT_UTF8`, `HEX`, `BINARY`.
  - `b`: Required, accepts `TEXT_UTF8`, `HEX`, `BINARY`.
  - `modulus`: Required, accepts `TEXT_UTF8`, `HEX`, `BINARY`.
  - Output: `TEXT_UTF8`.
- **Parameters**:
  - `operation`: Combo (`ADD`, `SUBTRACT`, `MULTIPLY`, `MODPOW`, `INVERSE`). Default `ADD`.
- **Facade**: `com.cryptocarver.crypto.ModularArithmetic.compute`.

### `UUID_GENERATE`
- **Category**: Utilities
- **Description**: Generates UUID (v4 random, v7 time-ordered, or v5 name-based SHA-1).
- **Ports**: No required input ports for v4/v7. For v5, accepts `input` payload.
- **Output**: `TEXT_UTF8` (UUID string).
- **Parameters**:
  - `version`: Combo (`v4`, `v7`, `v5`). Default `v4`.
- **Facade**: `com.cryptocarver.crypto.UUIDGenerator.generate`.

### `BYTE_STATISTICS`
- **Category**: Utilities
- **Description**: Calculates Shannon entropy, byte distribution frequency, and chi-square randomness metrics.
- **Ports**:
  - `input`: Required, accepts all representations.
  - Output: `TEXT_UTF8` (JSON or formatted statistics summary).
- **Parameters**: None.
- **Facade**: `com.cryptocarver.crypto.ByteStatistics.analyze`.

## 5. Key operations — Phase 5B.2a

Key-bearing graph ports require `HEX`; sensitive inspector parameters are marked
`PASSWORD`/`sensitive=true`, stripped from saved processes, and injected only in
the ephemeral executable copy.

| Type | Ports and output | Parameters | Delegated facade |
|---|---|---|---|
| `KCV` | `key: HEX` → `HEX` | method; full-block algorithm | `KeyOperations.calculateKCV_*`, `calculateFullZeroBlockKCV` |
| `KEY_SPLIT_XOR` | `key: HEX` → `HEX_COMPONENTS` share bundle | component count 2–5 | `KeyOperations.splitKey` |
| `KEY_COMBINE_XOR` | `components: HEX_COMPONENTS` → `HEX` | none | `KeyOperations.combineKeyComponents` |
| `PARITY_ADJUST` / `PARITY_CHECK` | `key: HEX` → `HEX` / UTF-8 | none | `KeyOperations.applyOddParity` / `detectParity` |
| `KDF_HKDF` | `ikm`, optional `salt/info: HEX` → binary | digest, output length | `KeyDerivation.hkdf` |
| `KDF_SP800_108` | `key`, optional `label/context: HEX` → binary | digest, output length | `KeyDerivation.sp800108Counter` |
| `KDF_X963` | `sharedSecret`, optional `sharedInfo: HEX` → binary | digest, output length | `KeyDerivation.x963` |
| `KDF_SCRYPT` / `KDF_ARGON2` | `password/salt: HEX` → binary | cost and output parameters | `KeyDerivation.scrypt` / `argon2` |
| AES wrap/unwrap RFC 3394/5649 | `kek/keyData: HEX`, wrapped binary → binary | none | `KeyWrapOperations` RFC methods |
| `TR31_WRAP` / `TR31_UNWRAP` | `kbpk/key: HEX`, block UTF-8 → UTF-8/HEX | TR-31 header fields | `TR31Operations.wrapKey` / `unwrapKey` |
| `TR31_PARSE_HEADER` | `keyBlock: UTF-8` → UTF-8 | none | `TR31Operations.parseHeader` |
| `ICSF_TOKEN_PARSE` | `token: HEX` → UTF-8 | provenance | `IcsfTokenParser.parse` + `IcsfTokenReport.renderText` |
| `KEYPAIR_GENERATE` | no input → PKCS#8 private binary | algorithm, size/curve | `AsymmetricKeyOperations` |
| `KEY_MATERIAL_INSPECT` | encoded key binary → UTF-8 | algorithm, key kind | `KeyMaterialInspector.describeKey` |

`RSA_KEYPAIR_GENERATE` remains a compatibility alias. `KEY_SPLIT_XOR` uses a
colon-delimited `HEX_COMPONENTS` bundle because the current one-output
`ProcessNodeHandler` SPI has no separate output-port value. It requires between
2 and 5 components, and every component must have the same byte length as the
others; the dedicated representation is accepted only by `components` on
`KEY_COMBINE_XOR` and `COMPONENT_SELECT`, so a plain `HEX` key port rejects the
bundle during validation. No share is written to the result area except under
`FULL_LAB`.
ICSF parsing never wraps, imports or exports a token.

## 6. Payment operations — Phase 5B.2b

Payment handlers are thin adapters over the existing payment, DUKPT, EMV and
TLV facades. PAN, PIN, PIN blocks, CVKs, PVKs, DUKPT material, EMV keys and
cryptograms are sensitive inspector parameters and remain transient. PAN inputs
are decimal, 13–19 digits, and pass Luhn validation through
`CheckDigitCalculator`; the handler does not reimplement Luhn.

| Type | Ports and output | Sensitive parameters | Delegated facade |
|---|---|---|---|
| `PIN_BLOCK_ENCODE` / `PIN_BLOCK_DECODE` / `PIN_BLOCK_TRANSLATE` | Text PIN/PAN and HEX block ports → HEX or text | PIN, PAN, PIN block | `PaymentOperations` PIN-block methods |
| `CVV_GENERATE` / `CVV_VERIFY` | CVK HEX plus PAN/expiry/service text → text | CVKs, PAN, CVV | `PaymentOperations.generateCVV` / `verifyCVV` |
| `DCVV_GENERATE` / `DCVV_VERIFY` | CVK HEX plus PAN/sequence/expiry/ATC text → text | CVKs, PAN, CVV | `PaymentOperations.generateDCVV` / `verifyDCVV` |
| `PVV_GENERATE` / `PVV_VERIFY` | PIN/PAN text and PVK HEX → text | PIN, PAN, PVK, PVV | `PaymentOperations.generatePVV` / `verifyPVV` |
| `IBM3624_OFFSET` | PIN/PAN text and PVK/decimalization HEX → text | PIN, PAN, PVK, decimalization table | `PaymentOperations.generateIBM3624Offset` |
| `DUKPT_TDES_DERIVE` | IPEK/KSN HEX → HEX | IPEK, KSN | `DukptKsn.deriveWorkingKey` |
| `DUKPT_AES_DERIVE` / `DUKPT_PIN_CRYPT` | BDK/KSN/PIN-block HEX → HEX | BDK, KSN, PIN block | `AesDukpt` |
| `EMV_ICC_MASTER_KEY` / `EMV_SESSION_KEY` | EMV key/data ports → HEX | IMK/MKAC, PAN, session inputs | `EMVOperations` derivation methods |
| `EMV_ARQC_GENERATE` / `EMV_ARQC_VERIFY` / `EMV_ARPC` | EMV key/cryptogram/data ports → HEX or text | session key, ARQC, transaction data, CSU | `EMVOperations` ARQC/ARPC methods |
| `EMV_TLV_PARSE` | EMV data HEX → text summary | EMV data | `EmvTlv.analyze` / `transactionSummary` |
| `TRACK2_ENCODE` / `TRACK2_PARSE` | PAN/track text → text | PAN, discretionary data, Track 2 | `PaymentOperations` Track 2 methods |

`KEY_SPLIT_XOR` is limited to components of equal byte length and a maximum of
five components. It emits one `HEX_COMPONENTS` value containing the
colon-delimited bundle; only `KEY_COMBINE_XOR.components` and
`COMPONENT_SELECT.components` accept that representation. A plain `HEX` port
must reject the bundle during `validate()`, before execution.

## 7. Envelopes and signatures — Phase 5B.3

All key, password, passphrase and CEK parameters are transient (`sensitive=true`). Verification nodes emit the
authenticated payload and fail on a bad signature, MAC, AEAD tag or trust decision. `STRUCTURAL_ONLY` is an explicit
opt-in without trust and carries a visible warning; `REQUIRE_TRUST` is the default and requires a local truststore.

| Type | Input ports (`*` required) | Output | Parameters (`!` transient) | Delegated facade |
|---|---|---|---|---|
| `JWS_SIGN` | `payload*`, `key` | `TEXT_UTF8` | algorithm (HS/RS/PS/ES 256-512), `key!` | `JOSEService.signJws` |
| `JWS_VERIFY` | `message*`, `key` | `TEXT_UTF8` (payload) | algorithm, `key!` | `JOSEService.verifyJws` |
| `JWS_DETACHED_SIGN` | `payload*`, `key` | `TEXT_UTF8` | algorithm, `key!`, unencoded payload | `JOSEService.generateDetachedJWS` |
| `JWS_DETACHED_VERIFY` | `message*`, `payload*`, `key` | `TEXT_UTF8` (payload) | algorithm, `key!` | `JOSEService.verifyDetachedJWS` |
| `JWE_ENCRYPT` | `payload*`, `cek` | `TEXT_UTF8` | key alg (`dir`, RSA-OAEP-256), content alg (A128/192/256GCM), `cek!` | `JOSEService.encryptJwe` |
| `JWE_DECRYPT` | `message*`, `cek` | `TEXT_UTF8` (plaintext) | `cek!` | `JOSEService.decryptJwe` |
| `JWT_INSPECT` | `message*` | `TEXT_UTF8` (report) | none — never verifies | `JOSEService.inspectJwt` |
| `COSE_SIGN1` | `payload*`, `privateKey`, `publicKey` | `BINARY` | algorithm (ES/PS 256-512, EdDSA), `privateKey!`, public key | `COSEOperations.sign1` |
| `COSE_VERIFY1` | `message*`, `publicKey` | `BINARY` (payload) | algorithm, public key | `COSEOperations.verify1` |
| `COSE_MAC0` | `payload*`, `key` | `BINARY` | algorithm (HS256-512), `key!` | `COSEOperations.mac0` |
| `COSE_VERIFY_MAC0` | `message*`, `key` | `BINARY` (payload) | `key!` | `COSEOperations.verifyMac0` |
| `COSE_ENCRYPT0` | `payload*`, `cek` | `BINARY` | algorithm (A128/192/256GCM), `cek!` | `COSEOperations.encrypt0` |
| `COSE_DECRYPT0` | `message*`, `cek` | `BINARY` (plaintext) | `cek!` | `COSEOperations.decrypt0` |
| `CMS_SIGN` | `payload*` | `BINARY` | keystore path/alias, `keystorePassword!`, `keyPassword!`, detached | `CMSOperations.generateSignedData` |
| `CMS_VERIFY` | `message*`, `payload` | `BINARY` (payload) | verification mode, truststore path, `trustStorePassword!` | `CMSOperations.verifySignedData` |
| `CMS_ENVELOPE` | `payload*` | `BINARY` | recipient certificate path | `CMSOperations.generateEnvelopedData` |
| `CMS_DEVELOPE` | `message*` | `BINARY` (plaintext) | keystore path/alias, `keystorePassword!`, `keyPassword!` | `CMSOperations` enveloped recovery |
| `CADES_BES_SIGN` | `payload*` | `BINARY` | keystore path/alias, `keystorePassword!`, `keyPassword!` | `CMSOperations.generateCadesBes` |
| `XMLDSIG_SIGN` | `payload*` (XML) | `TEXT_UTF8` | keystore path/alias, passwords`!`, packaging (enveloped/enveloping/detached) | `XMLSignatureOperations.signXAdES` |
| `XMLDSIG_VERIFY` | `message*` (XML) | `TEXT_UTF8` (authenticated XML) | verification mode, truststore, `trustStorePassword!` | `XMLSignatureOperations.verifyXAdESPayload` |
| `PADES_SIGN` | `payload*` (PDF) | `BINARY` | keystore path/alias, passwords`!`, profile **B only** | `PadesOperations.signBaselineB` |
| `PADES_VERIFY` | `message*` (PDF) | `BINARY` (verified PDF) | verification mode, truststore, `trustStorePassword!` | `PadesOperations` verification |
| `OPENPGP_ENCRYPT` | `payload*` | `TEXT_UTF8` (armored) | recipient public key | `OpenPgpOperations.encrypt` |
| `OPENPGP_DECRYPT` | `message*` | `BINARY` (plaintext) | `privateKey!`, `passphrase!` | `OpenPgpOperations.decrypt` |
| `OPENPGP_SIGN` | `payload*` | `TEXT_UTF8` (armored) | `privateKey!`, `passphrase!` | `OpenPgpOperations` signing |
| `OPENPGP_VERIFY` | `message*`, `publicKey` | `BINARY` (payload) | signer public key | `OpenPgpOperations` verification |
| `PQC_KEYPAIR_GENERATE` | none | `BINARY` (PKCS#8) | ML-DSA 44/65/87, SLH-DSA 128f/192f/256f, ML-KEM 512/768/1024 | `PostQuantumOperations.generateKeyPair` |
| `PQC_SIGN` | `payload*`, `privateKey` | `BINARY` | ML-DSA / SLH-DSA parameter set, `privateKey!` | `PostQuantumOperations.sign` |
| `PQC_VERIFY` | `payload*`, `signature*`, `publicKey` | `BINARY` (payload) | parameter set, public key | `PostQuantumOperations.verify` |
| `PQC_KEM_ENCAPSULATE` | `publicKey` | `BINARY` | ML-KEM 512/768/1024, public key | `PostQuantumOperations.encapsulate` |
| `PQC_KEM_DECAPSULATE` | `encapsulation*`, `privateKey` | `BINARY` (shared secret) | ML-KEM parameter set, `privateKey!` | `PostQuantumOperations.decapsulate` |
| `CERT_PARSE` | `certificate*` | `TEXT_UTF8` (description) | none | `KeyMaterialInspector.describeCertificate` |
| `CERT_SELF_SIGNED_GENERATE` | none | `BINARY` (DER) | common name, RSA/EC, size, validity days | `CertificateGenerator.generateSelfSignedCertificate` |
| `CERT_VALIDATE` | `certificate*` | `TEXT_UTF8` (verdict) | verification mode, truststore, `trustStorePassword!` | `CertificateGenerator.validateAgainstTrustStore` |

PAdES T/LT/LTA and timestamped CAdES are excluded because the available signing APIs require a remote TSA URL;
there is no local timestamp-token input. Online OCSP/CRL, downloads and external XML resources are disabled. XML
rejects DOCTYPE and external entities before crypto. SHA-1 signatures are not offered, and RSA-OAEP SHA-1 is rejected
in favour of RSA-OAEP-256. CMS EnvelopedData never emits its generated CEK.
