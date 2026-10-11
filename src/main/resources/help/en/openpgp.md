# OpenPGP laboratory

The Java OpenPGP panel creates fixed 3072-bit RSA key pairs and handles armored text, binary files, and detached, attached, or clear-signed messages. Detached verification requires the exact original data, detached signature, and public key. Attached and clear-signed verification read the combined signed message. Signature inspection reports public metadata; it does not verify integrity or Web-of-Trust.

The secret key and passphrase are used only by explicit signing/decryption operations. Public and secret material can be pasted, loaded, saved, inspected, or selected from the local Shelf. File operations use explicit file choosers and have a 16 MiB limit. Help only reports whether fields contain text; it does not parse key material or read payloads.

GnuPG probing, verification, and the isolated interoperability check are optional external actions. An unavailable GnuPG binary does not disable the Java OpenPGP implementation. Key generation, file access, cryptographic work, and external process invocation occur only when their corresponding action is selected.
