# RSA encryption

The asymmetric cipher panel encrypts with an RSA public key and decrypts with the matching private key. Public-key parsing tries SubjectPublicKeyInfo PEM, Hex-encoded X.509 DER, then Base64-encoded X.509 DER. Private-key parsing tries PKCS#8 PEM, Hex-encoded PKCS#8 DER, then Base64-encoded PKCS#8 DER. Certificate PEM and PKCS#1 `RSA PRIVATE KEY` PEM are not accepted by these handlers. The input/output selectors are exactly Text (UTF-8), Hexadecimal, Base64, and Binary; Base64URL is not offered. The displayed `RSA/ECB/...` transformation is a JCE naming convention; RSA is not symmetric ECB.

For a modulus of *k* bytes, the implementation's byte limits are *k − 11* for PKCS#1 v1.5, *k − 42* for OAEP-SHA-1, and *k − 66* for OAEP-SHA-256. The current Bouncy Castle transformations use OAEP digest/MGF1 pairs SHA-1/SHA-1 and SHA-256/SHA-256, respectively. The panel derives a limit only from already-loaded public RSA metadata. If visible key text has been replaced but not loaded, help clears the old maximum; it does not parse key text or inspect a private key. `NoPadding` has a separate raw RSA rule and no padded maximum.

Use a short test payload and matching key pair for a positive round trip. Inputs above the selected limit and ciphertext from a different key family fail. Switching padding changes the applicable size limit without running RSA.
