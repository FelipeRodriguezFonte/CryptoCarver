# Mapa de arquitectura de información

Este es el inventario canónico de navegación de CryptoCarver. Cada operación
aparece una sola vez; Favoritos, Recientes, búsqueda, paleta y breadcrumbs son
accesos directos al destino canónico y no módulos adicionales. No se mueve una
operación entre secciones sin aprobación explícita del propietario.

Los grupos tienen como objetivo mantener entre 3 y 9 hojas cuando el volumen de
la sección lo permite. `HIGH` y `EXTREME` indican que la operación puede manejar
material secreto; `LOW` puede recibir material sensible no secreto y `NONE` no
debería manejar secretos. El estado debe coincidir con `OperationRegistry`.

| Sección | Grupo | Operación | ID canónico | Estado | Sensibilidad | Alias |
|---|---|---|---|---|---|---|
| Cifrado | Simétrico | Symmetric Ciphers | `op_sym_ciphers` | STABLE | HIGH | AES, DES, 3DES |
| Cifrado | Simétrico | File Cipher (Streaming) | `op_sym_file` | EXPERIMENTAL | HIGH | — |
| Cifrado | Asimétrico | Asymmetric Ciphers | `op_asym_ciphers` | STABLE | HIGH | RSA |
| Cifrado | OpenPGP | OpenPGP (GPG Compatible) | `op_openpgp` | EXPERIMENTAL | HIGH | GPG, PGP |
| Genérico | Conversión y archivos | Manual Conversion | `op_gen_manual` | STABLE | NONE | Hex, Base64, EBCDIC |
| Genérico | Conversión y archivos | File Conversion | `op_gen_file` | STABLE | NONE | — |
| Genérico | Conversión y archivos | Compressed Hex (2-row) | `op_gen_compressed_hex` | STABLE | NONE | Host hex, Interleaved hex, Two-row hex |
| Genérico | Inspección | Crypto Envelope Inspector | `op_gen_envelope_inspector` | STABLE | LOW | Envelope, CMS, JWE, crypto-agility |
| Genérico | Automatización | Batch Runner | `op_gen_batch` | STABLE | LOW | — |
| Genérico | Automatización | Process Designer | `op_gen_process_designer` | EXPERIMENTAL | LOW | workflow, canvas, pipeline |
| Genérico | Cálculo | Hashing | `op_gen_hash` | STABLE | NONE | SHA, MD5 |
| Genérico | Cálculo | Check Digits | `op_gen_check_digits` | STABLE | NONE | Luhn |
| Genérico | Cálculo | Modular Arithmetic | `op_gen_mod` | STABLE | NONE | — |
| Genérico | Utilidades | Random Number Generator | `op_gen_random` | STABLE | NONE | — |
| Genérico | Utilidades | Clipboard Shelf | `op_gen_clipboard` | STABLE | LOW | Clipboard, Copy history, Shelf |
| Autenticación | Firmas y MAC | Digital Signatures | `op_auth_sig` | STABLE | HIGH | RSA, ECDSA |
| Autenticación | Firmas y MAC | Message Authentication Codes | `op_auth_mac` | STABLE | HIGH | HMAC, CMAC |
| Claves | Laboratorio | Key Lab | `op_keys_lab` | STABLE | HIGH | — |
| Claves | Generación | Key Generation | `op_keys_gen` | STABLE | HIGH | — |
| Claves | Generación | RSA Key Generation | `op_keys_rsa` | STABLE | HIGH | — |
| Claves | Generación | ECDSA Key Generation | `op_keys_ecdsa` | STABLE | HIGH | — |
| Claves | Generación | DSA Key Generation | `op_keys_dsa` | STABLE | HIGH | — |
| Claves | Generación | EdDSA Key Generation | `op_keys_eddsa` | STABLE | HIGH | — |
| Claves | Validación | Validation & KCV | `op_keys_val` | STABLE | HIGH | — |
| Claves | Validación | Compare Public / Private Key | `op_keys_compare` | STABLE | HIGH | — |
| Claves | Derivación y reparto | Key Sharing (XOR Split/Combine) | `op_keys_share` | STABLE | HIGH | — |
| Claves | Derivación y reparto | Key Derivation (KDF) | `op_keys_kdf` | STABLE | HIGH | HKDF, PBKDF2 |
| Claves | Envoltura y transporte | AES Key Wrap | `op_keys_wrap` | STABLE | HIGH | RFC 3394 |
| Claves | Envoltura y transporte | TR-31 Key Blocks | `op_keys_tr31` | STABLE | HIGH | TR-31, TR31 |
| Claves | Envoltura y transporte | RSA Key Exchange | `op_keys_kex_rsa` | STABLE | HIGH | RSA-OAEP, Key Transport, TR-34 |
| Claves | Envoltura y transporte | TR-34 Key Distribution | `op_keys_tr34` | EXPERIMENTAL | HIGH | TR-34, Remote Key Loading, KDH, KRD |
| Claves | HSM y almacenes | Key Material Inspector | `op_keys_material` | STABLE | LOW | — |
| Claves | HSM y almacenes | Key & Certificate Format Workbench | `op_keys_format_workbench` | STABLE | HIGH | PEM, DER, JWK, PKCS12 |
| Claves | HSM y almacenes | KeyStore Inspector | `op_keys_store` | STABLE | LOW | JKS, PKCS12 |
| Claves | HSM y almacenes | PKCS#11 Token | `op_keys_pkcs11` | EXPERIMENTAL | HIGH | HSM, SunPKCS11, SoftHSM |
| Claves | HSM y almacenes | ICSF / CCA Key Token Analyzer | `op_keys_icsf_token` | EXPERIMENTAL | LOW | ICSF, CCA, z/OS, CKDS |
| Claves | HSM y almacenes | ICSF / CCA Batch Analysis | `op_keys_icsf_batch` | EXPERIMENTAL | LOW | ICSF, CCA, z/OS, batch |
| Claves | HSM y almacenes | ICSF / CCA Key Export / Import | `op_keys_icsf_keywrap` | EXPERIMENTAL | HIGH | ICSF, CCA, CSNBKEX, CSNBKIM, KEK |
| Post-Quantum | Claves y firmas | PQC Key Generation | `op_pqc_gen` | EXPERIMENTAL | HIGH | ML-KEM, Kyber, ML-DSA, Dilithium |
| Post-Quantum | Claves y firmas | PQC Sign/Verify | `op_pqc_sign` | EXPERIMENTAL | HIGH | SLH-DSA, SPHINCS+ |
| Seguridad XML | Firmas XML | Sign XML (XAdES) | `op_xml_sign` | STABLE | HIGH | XAdES |
| Seguridad XML | Firmas XML | Verify XML (XAdES) | `op_xml_verify` | STABLE | LOW | — |
| Seguridad XML | Inspección y tiempo | Inspect Signed XML | `op_xml_inspect` | STABLE | LOW | — |
| Seguridad XML | Inspección y tiempo | RFC 3161 Timestamp | `op_xml_tsa` | STABLE | LOW | RFC 3161 |
| Seguridad XML | WS-Security | Sign SOAP (WSS) | `op_wss_sign` | EXPERIMENTAL | HIGH | WSS, SOAP |
| Seguridad XML | WS-Security | Verify SOAP (WSS) | `op_wss_verify` | EXPERIMENTAL | LOW | WSS, SOAP |
| Seguridad XML | WS-Security | Add UsernameToken (WSS) | `op_wss_username_add` | EXPERIMENTAL | HIGH | WSS, SOAP, UsernameToken |
| Seguridad XML | WS-Security | Verify UsernameToken (WSS) | `op_wss_username_verify` | EXPERIMENTAL | HIGH | WSS, SOAP, UsernameToken |
| Seguridad XML | WS-Security | Encrypt SOAP Body (WSS) | `op_wss_encrypt` | EXPERIMENTAL | LOW | WSS, SOAP, XML Encryption |
| Seguridad XML | WS-Security | Decrypt SOAP Body (WSS) | `op_wss_decrypt` | EXPERIMENTAL | HIGH | WSS, SOAP, XML Encryption |
| Certificados | Emisión | Generate Certificate | `op_cert_gen` | STABLE | HIGH | — |
| Certificados | Emisión | Issue Certificate from CSR | `op_cert_issue` | STABLE | HIGH | CSR |
| Certificados | Validación | Parse Certificate | `op_cert_parse` | STABLE | LOW | — |
| Certificados | Validación | Validate Certificate | `op_cert_val` | STABLE | LOW | — |
| Certificados | Validación | Compare Certificates | `op_cert_compare` | STABLE | LOW | — |
| Certificados | Validación | Certificate Chain | `op_cert_chain` | STABLE | LOW | — |
| Certificados | Firmas y contenedores | CMS/PKCS#7 Operations | `op_cert_cms` | STABLE | HIGH | PKCS7, CMS |
| Certificados | Firmas y contenedores | CMS Inspector | `op_cms_inspector` | STABLE | LOW | PKCS7, CMS, SignedData, EnvelopedData |
| Certificados | Firmas y contenedores | PAdES PDF Signatures | `op_pades` | EXPERIMENTAL | HIGH | PDF, PAdES |
| Certificados | Firmas y contenedores | ASiC-S Containers | `op_asic_s` | EXPERIMENTAL | HIGH | ASiC, ASiC-S, CAdES |
| JOSE | Tokens y claves | JWT (Signed) | `op_jose_jwt` | STABLE | HIGH | JWS |
| JOSE | Tokens y claves | JWE (Encrypted) | `op_jose_jwe` | STABLE | HIGH | JWE, JWE Encryption, JWE Decryption |
| JOSE | Tokens y claves | JWK (Keys) | `op_jose_jwk` | STABLE | HIGH | JWKS |
| JOSE | Tokens y claves | JWA (Algorithms) | `op_jose_jwa` | STABLE | NONE | — |
| JOSE | Tokens y claves | Token Inspector | `op_jose_insp` | STABLE | LOW | — |
| COSE | Mensajes CBOR | COSE_Sign1 | `op_cose_sign1` | EXPERIMENTAL | HIGH | CBOR, RFC 9052 |
| COSE | Mensajes CBOR | COSE_Mac0 | `op_cose_mac0` | EXPERIMENTAL | HIGH | CBOR, RFC 9052, HMAC |
| COSE | Mensajes CBOR | COSE_Encrypt0 | `op_cose_encrypt0` | EXPERIMENTAL | HIGH | CBOR, RFC 9052, AES-GCM |
| Pagos | PIN y CVV | Clear PIN Blocks | `op_pay_clear_pin` | STABLE | HIGH | — |
| Pagos | PIN y CVV | Encrypted PIN Blocks | `op_pay_enc_pin` | STABLE | HIGH | — |
| Pagos | PIN y CVV | PIN Generation | `op_pay_pin_gen` | STABLE | HIGH | — |
| Pagos | PIN y CVV | CVV Operations | `op_pay_cvv` | STABLE | HIGH | CVV, CVC |
| Pagos | EMV y DUKPT | DUKPT TDES / AES | `op_pay_dukpt` | STABLE | HIGH | DUKPT |
| Pagos | EMV y DUKPT | EMV TLV Inspector | `op_pay_emv_tlv` | STABLE | LOW | EMV |
| Pagos | EMV y DUKPT | EMV Operations | `op_pay_emv_ops` | STABLE | HIGH | — |
| ASN.1 | Codificación | Decode ASN.1 | `op_asn1_dec` | STABLE | LOW | — |
| ASN.1 | Codificación | Encode ASN.1 | `op_asn1_enc` | STABLE | LOW | — |
| Historial | Operaciones | Recent Operations | `op_hist_recent` | STABLE | NONE | — |
| Historial | Sesiones | Saved Sessions | `op_hist_saved` | STABLE | NONE | — |
| Historial | Exportación | Export History | `op_hist_export` | STABLE | NONE | — |

## Reglas de mantenimiento

- La fuente de verdad técnica es `OperationRegistry`; este mapa debe revisarse
  cuando se añada, retire o cambie una operación.
- Un acceso directo no crea una segunda copia del módulo: apunta al ID
  canónico, conserva su sección y respeta su nivel de sensibilidad.
- Favoritos y Recientes muestran como máximo cinco filas y no alteran el árbol
  canónico. Las fechas de Recientes se muestran únicamente en el tooltip.
- Los módulos nuevos requieren revisión del propietario antes de modificar la
  sección o el grupo de una operación existente.
