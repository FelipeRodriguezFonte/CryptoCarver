# Crypto envelope inspection

Inspection reads the envelope’s encoded structure and public metadata; it does not recover a key or prove sender authenticity. Unwrap is explicit and profile-specific (Raw OAEP, JWE Compact, CMS EnvelopedData in the current selector), and requires compatible private material. The recovered key is sensitive output.
