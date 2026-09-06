# Process Designer Operation Reference (Ola 5B.1)

This document provides reference documentation for all 23 node operations introduced in **Ola 5B.1 (Plumbing & Representation)** of the Visual Process Designer.

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
`KEY_COMBINE_XOR`, so a plain `HEX` key port rejects the bundle during
validation. No share is written to the result area except under `FULL_LAB`.
ICSF parsing never wraps, imports or exports a token.
