# JWE reference

JSON Web Encryption (JWE) provides authenticated encryption for a payload. The selected `alg` manages or derives a content-encryption key (CEK); `enc` encrypts and authenticates the payload. They are separate choices.

## Serialization and recipients

- Compact serialization has five Base64URL segments, one recipient, and no external `aad` member.
- Flattened JSON represents one recipient. General JSON uses a recipients array and can represent multiple recipients when the selected algorithms support them.
- External AAD is UTF-8 text in this application. JSON serialization carries it as the JWE `aad` member; it is authenticated but not encrypted.
- For General JSON multi-recipient input, recipient JWKs provide `alg` and any per-recipient `kid`, `apu`, and `apv` values. Direct `ECDH-ES` is limited to one recipient. `dir` supplies the content key directly.
- With multiple General JSON recipients, the composer rejects any nonblank global `kid`, `apu`, or `apv`; put those values in the recipient JWKs. Each recipient JWK must declare `alg`.

## Key management options

RSA key transport requires a public key for encryption and matching private key for decryption. `RSA1_5` is available for compatibility; OAEP variants select the hash named by the JWA identifier. AES-KW and AES-GCMKW use a shared key-encryption key. GCMKW has its own wrapping IV and tag, distinct from the content IV and tag.

ECDH-ES uses a recipient EC or OKP key. The direct variant derives the CEK; the `+AxxxKW` variants derive an AES key-encryption key. `apu` and `apv` are optional party information. PBES2-HS256+A128KW, PBES2-HS384+A192KW, and PBES2-HS512+A256KW derive a key-encryption key from a password and use the configured iteration count. A blank iterations field defaults to 600000; a nonblank value must parse as a whole integer of at least 1000. The current UI applies that validation to every encryption `alg`, including non-PBES2 choices. This documents the application's input rule and does not state a universal policy. `dir` uses the provided symmetric key directly as the CEK, whose required length is determined by `enc`.

## Content encryption and secret formats

`A128GCM`, `A192GCM`, and `A256GCM` use 16, 24, and 32-byte CEKs. `A128CBC-HS256`, `A192CBC-HS384`, and `A256CBC-HS512` use 32, 48, and 64-byte CEKs. Shared secret/password inputs can be interpreted as UTF-8, Hex, or Base64/Base64URL. UTF-8 converts the entered text directly; Hex ignores whitespace and an optional `0x` prefix; Base64/Base64URL ignores whitespace. The encoding controls bytes; it does not alter the algorithm. The decryption format selector is local to decryption and independent of the encryption selector.

For encryption, recipient key material accepts PEM (including supported RSA/EC key forms), an X.509 certificate, Base64-encoded DER, a JWK, or a single-key JWKS. Decryption requires a matching private key in a supported PEM, Base64 DER, private JWK, or single-key JWKS form; an X.509 certificate supplies only a public key. General JSON multi-recipient encryption takes a JWKS with at least two recipient keys, each with its own `alg`.

## Headers and outputs

`typ` labels the JWE object; `cty` describes its payload (use `JWT` when the payload is itself a JWT). `kid` identifies a recipient key. The ECDH party values `apu` and `apv` are optional. Custom header JSON is subject to the composer's exact reserved-name check: `alg`, `enc`, `zip`, `epk`, `iv`, `tag`, `p2s`, and `p2c`. For example, `{"note":"demo"}` is accepted, while `{"alg":"dir"}` and `{"p2c":600000}` are rejected. The payload and decryption material are sensitive. The contextual help receives only allowlisted selector codes and derived empty/non-empty flags; it never reads their text. Opening help for decryption does not parse the received JWE or infer its `alg`, `enc`, or serialization from it or from previous results.

The visual breakdown distinguishes the protected header, encrypted key, content IV, ciphertext, and content authentication tag. A recovered CEK is sensitive key material; help does not retrieve or display it. For GCMKW, wrapping IV/tag and content IV/tag are separate.

## Synthetic examples and diagnostics

For a single recipient, encrypt a synthetic message with an RSA test public key using `RSA-OAEP-256`, `A256GCM`, and Compact; decrypt with the matching private test key. With `dir`, choose `A256GCM` and a 32-byte test secret. For multiple recipients, choose General JSON and provide at least two compatible recipient JWKs with `alg` values.

Expected negative cases: non-empty AAD with Compact is rejected; a `dir` secret whose decoded length does not match `enc` is rejected; changing the authentication tag causes decryption/authentication failure. These checks describe current `JweComposer` behavior and do not promise broader algorithm support.

## References

- [RFC 7516: JSON Web Encryption (JWE)](https://www.rfc-editor.org/rfc/rfc7516.html)
- [RFC 7518: JSON Web Algorithms (JWA)](https://www.rfc-editor.org/rfc/rfc7518.html)
- [RFC 7519: JSON Web Token (JWT)](https://www.rfc-editor.org/rfc/rfc7519.html)
- Implementation: `com.cryptocarver.crypto.JweComposer`, `com.cryptocarver.ui.JoseJweCoordinator`, and `src/main/resources/fxml/jose.fxml`.
