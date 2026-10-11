# Symmetric cipher workspace

This local reference follows `SymmetricCipher`, `SymmetricCipherCoordinator`, `SymmetricFieldsPresenter`, and the format profile registered for Symmetric Ciphers.

## Parameters and applicability

The manual key is hexadecimal. AES-128/192/256 uses 16/24/32 key bytes; DES uses 8; 3DES accepts 16 or 24 bytes. The simulated HSM source selects a local Key Lab reference. Help context records only public selector codes and whether material is present; it never reads key, payload, tag, AAD, clipboard, or path text.

ECB has no IV and the UI warns about repeated plaintext blocks. CBC uses an IV and may use selected padding. CTR, CFB, OFB, and GCM force `NoPadding` in the UI. Stream algorithms disable the mode and padding selectors while retaining their prior values. Compatibility beyond these UI rules is determined by the active crypto implementation/provider; the selector does not filter every block-algorithm/mode pair.

The generated IV/nonce sizes are recommendations: AES block modes 16 bytes, DES/3DES and Salsa20 8, GCM and ChaCha20 variants 12, and XChaCha20-Poly1305 24. Plain ChaCha20 accepts 8- or 12-byte nonces in the core; its UI generator produces 12. ChaCha20-Poly1305 requires 12 bytes and XChaCha20-Poly1305 requires 24. The GCM core accepts nonce lengths 8, 12, or 16 bytes and generates 12. GCM and the Poly1305 variants expose a 16-byte tag. Plain Salsa20/ChaCha20 and block modes other than GCM do not authenticate ciphertext.

### Padding options

`NoPadding` adds no bytes. For block transformations that require whole blocks, such as ECB/CBC without padding, the input must already be block-aligned. The UI forces `NoPadding` for CTR, CFB, OFB, and GCM and disables padding for stream algorithms. For supported ECB/CBC transformations, PKCS5/PKCS7 add a full block when the input is already aligned; each added byte contains the number of padding bytes. The application maps its `PKCS7Padding` choice to the provider transformation name `PKCS5Padding`. ISO10126 uses random bytes followed by the padding length; ISO7816-4 uses `0x80` followed by zeroes. ZeroBytePadding appends zeroes only when needed, and decryption strips trailing zeroes, so data originally ending in zero bytes is ambiguous. Provider support still determines whether a particular algorithm/mode/padding combination works.

GCM accepts optional AAD. In the block GCM path, AAD is parsed as hexadecimal, with a US-ASCII fallback when parsing fails. The current dedicated ChaCha20-Poly1305 and XChaCha20-Poly1305 handlers display AAD but do not pass it to the operation. This is a documented implementation limitation, not a behavior changed by help.

Never reuse a nonce with the same key for GCM or stream ciphers. The app warns about reuse within its current session; this does not make nonce reuse safe.

## Reproducible example

Use synthetic values only: AES-256/GCM; key `000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F`; nonce `000102030405060708090A0B`; AAD `101112`; UTF-8 payload `test`. Encryption returns ciphertext and a separate 16-byte tag. Retain ciphertext, nonce, tag, and AAD. Decrypting with the same key and values recovers `test`. Altering one tag byte must fail authentication. A wrong-length AES key is rejected before a valid operation.

The global payload format controls payload byte decoding/encoding. Key, IV/nonce, tag, and the block GCM AAD field have their own hexadecimal handling.
