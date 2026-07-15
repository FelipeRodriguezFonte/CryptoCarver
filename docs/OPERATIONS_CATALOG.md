# CryptoCarver Operations Catalog

This document is generated automatically from `OperationRegistry`. Do not edit manually.

## ASN1

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 📖 | Decode ASN.1 | `op_asn1_dec` | STABLE | LOW | `Decode ASN.1` | - |
| 🖋 | Encode ASN.1 | `op_asn1_enc` | STABLE | LOW | `Encode ASN.1` | - |

## Authentication

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| ✒ | Digital Signatures | `op_auth_sig` | STABLE | HIGH | `Digital Signatures` | RSA, ECDSA |
| 🛡 | Message Authentication Codes | `op_auth_mac` | STABLE | HIGH | `Message Authentication Codes` | HMAC, CMAC |

## Certificates

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 🗃 | CMS/PKCS#7 Operations | `op_cert_cms` | STABLE | HIGH | `CMS/PKCS#7 Operations` | PKCS7, CMS |
| 🔗 | Certificate Chain | `op_cert_chain` | STABLE | LOW | `Certificate Chain` | - |
| ⚖ | Compare Certificates | `op_cert_compare` | STABLE | LOW | `Compare Certificates` | - |
| 📜 | Generate Certificate | `op_cert_gen` | STABLE | HIGH | `Generate Certificate` | - |
| 🖋 | Issue Certificate from CSR | `op_cert_issue` | STABLE | HIGH | `Issue Certificate from CSR` | CSR |
| 📖 | Parse Certificate | `op_cert_parse` | STABLE | LOW | `Parse Certificate` | - |
| ✅ | Validate Certificate | `op_cert_val` | STABLE | LOW | `Validate Certificate` | - |

## Cipher

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 🔑 | Asymmetric Ciphers | `op_asym_ciphers` | STABLE | HIGH | `Asymmetric Ciphers` | RSA |
| 📄 | File Cipher (Streaming) | `op_sym_file` | EXPERIMENTAL | HIGH | `File Cipher (Streaming)` | - |
| 🔒 | Symmetric Ciphers | `op_sym_ciphers` | STABLE | HIGH | `Symmetric Ciphers` | AES, DES, 3DES |

## Generic

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| ⚙ | Batch Runner | `op_gen_batch` | STABLE | LOW | `Batch Runner` | - |
| 🔢 | Check Digits | `op_gen_check_digits` | STABLE | NONE | `Check Digits` | Luhn |
| 📁 | File Conversion | `op_gen_file` | STABLE | NONE | `File Conversion` | - |
| 🧩 | Hashing | `op_gen_hash` | STABLE | NONE | `Hashing` | SHA, MD5 |
| 🔄 | Manual Conversion | `op_gen_manual` | STABLE | NONE | `Manual Conversion` | Hex, Base64, EBCDIC |
| 🧮 | Modular Arithmetic | `op_gen_mod` | STABLE | NONE | `Modular Arithmetic` | - |
| 🎲 | Random Number Generator | `op_gen_random` | STABLE | NONE | `Random Number Generator` | - |

## History

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 📤 | Export History | `op_hist_export` | STABLE | NONE | `Export History` | - |
| 🕒 | Recent Operations | `op_hist_recent` | STABLE | NONE | `Recent Operations` | - |
| 💾 | Saved Sessions | `op_hist_saved` | STABLE | NONE | `Saved Sessions` | - |

## JOSE

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| ⚙ | JWA (Algorithms) | `op_jose_jwa` | STABLE | NONE | `JWA (Algorithms)` | - |
| 🔒 | JWE (Encrypted) | `op_jose_jwe` | STABLE | HIGH | `JWE (Encrypted)` | JWE |
| 🔑 | JWK (Keys) | `op_jose_jwk` | STABLE | HIGH | `JWK (Keys)` | JWKS |
| 🏷 | JWT (Signed) | `op_jose_jwt` | STABLE | HIGH | `JWT (Signed)` | JWS |
| 🔍 | Token Inspector | `op_jose_insp` | STABLE | LOW | `Token Inspector` | - |

## Keys

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 🎁 | AES Key Wrap | `op_keys_wrap` | STABLE | HIGH | `AES Key Wrap` | RFC 3394 |
| ⚖ | Compare Public / Private Key | `op_keys_compare` | STABLE | HIGH | `Compare Public / Private Key` | - |
| 🗝 | DSA Key Generation | `op_keys_dsa` | STABLE | HIGH | `DSA Key Generation` | - |
| 🗝 | ECDSA Key Generation | `op_keys_ecdsa` | STABLE | HIGH | `ECDSA Key Generation` | - |
| 🗝 | EdDSA Key Generation | `op_keys_eddsa` | STABLE | HIGH | `EdDSA Key Generation` | - |
| 🧬 | Key Derivation (KDF) | `op_keys_kdf` | STABLE | HIGH | `Key Derivation (KDF)` | HKDF, PBKDF2 |
| 🔑 | Key Generation | `op_keys_gen` | STABLE | HIGH | `Key Generation` | - |
| 🔎 | Key Material Inspector | `op_keys_material` | STABLE | LOW | `Key Material Inspector` | - |
| ✂ | Key Sharing (XOR Split/Combine) | `op_keys_share` | STABLE | HIGH | `Key Sharing (XOR Split/Combine)` | - |
| 🗄 | KeyStore Inspector | `op_keys_store` | STABLE | LOW | `KeyStore Inspector` | JKS, PKCS12 |
| 🗝 | RSA Key Generation | `op_keys_rsa` | STABLE | HIGH | `RSA Key Generation` | - |
| 📦 | TR-31 Key Blocks | `op_keys_tr31` | STABLE | HIGH | `TR-31 Key Blocks` | TR-31, TR31 |
| ✅ | Validation & KCV | `op_keys_val` | STABLE | HIGH | `Validation & KCV` | - |

## Payments

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 💳 | CVV Operations | `op_pay_cvv` | STABLE | HIGH | `CVV Operations` | CVV, CVC |
| 💳 | Clear PIN Blocks | `op_pay_clear_pin` | STABLE | HIGH | `Clear PIN Blocks` | - |
| 🔑 | DUKPT TDES / AES | `op_pay_dukpt` | STABLE | HIGH | `DUKPT TDES / AES` | DUKPT |
| 💳 | EMV Operations | `op_pay_emv_ops` | STABLE | HIGH | `EMV Operations` | - |
| 🔍 | EMV TLV Inspector | `op_pay_emv_tlv` | STABLE | LOW | `EMV TLV Inspector` | EMV |
| 🔒 | Encrypted PIN Blocks | `op_pay_enc_pin` | STABLE | HIGH | `Encrypted PIN Blocks` | - |
| 🔢 | PIN Generation | `op_pay_pin_gen` | STABLE | HIGH | `PIN Generation` | - |

## Post-Quantum

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 🔮 | PQC Key Generation | `op_pqc_gen` | EXPERIMENTAL | HIGH | `PQC Key Generation` | ML-KEM, Kyber, ML-DSA, Dilithium |
| ✒ | PQC Sign/Verify | `op_pqc_sign` | EXPERIMENTAL | HIGH | `PQC Sign/Verify` | SLH-DSA, SPHINCS+ |

## XML Security

| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |
|------|-------|----|--------|------|-----------------|---------|
| 🔍 | Inspect Signed XML | `op_xml_inspect` | STABLE | LOW | `Inspect Signed XML` | - |
| ⏱ | RFC 3161 Timestamp | `op_xml_tsa` | STABLE | LOW | `RFC 3161 Timestamp` | RFC 3161 |
| 📝 | Sign XML (XAdES) | `op_xml_sign` | STABLE | HIGH | `Sign XML (XAdES)` | XAdES |
| ✅ | Verify XML (XAdES) | `op_xml_verify` | STABLE | LOW | `Verify XML (XAdES)` | - |

