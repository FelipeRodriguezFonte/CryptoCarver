# Hashing and CRC32

A digest is computed over bytes, not the visible appearance of text. Hashing’s global selectors accept Text UTF-8, Hexadecimal, Base64 and Binary input; output accepts Hexadecimal, Base64 or Binary. Positive example: SHA-256 of UTF-8 `abc` is `BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD` (32 bytes). Odd Hex or characters outside `[0-9A-F]` are invalid.

SHA-224/256/384/512 and SHA3-256/512 are separate families; MD4, MD5 and SHA-1 are legacy and are not recommended for collision resistance. WHIRLPOOL and TIGER-192 return 64 and 24 bytes. CRC32/ISO-HDLC and ISO-HDLC, BZIP2, MPEG-2, POSIX, JAMCRC and iSCSI variants return 4 bytes, but detect accidental changes only; they do not authenticate. Changing input representation can change the bytes and therefore the digest.

Applying a template configures non-secret values without running the hash. Template controls save/select preferences; help does not include personal names or input/output. Implementation: `HashingCoordinator`, `HashOperations`, `OperationFormatRegistry` and `generic.fxml`.
