# Format-preserving encryption

CryptoCarver's FPE panel applies FF1 or FF3-1 to a string over an ordered alphabet. It preserves the string length and alphabet, but provides no integrity check and does not hide the alphabet. The AES key is Hex encoded and must contain 16, 24, or 32 bytes. The tweak is optional for FF1 and exactly seven bytes for FF3-1; use the same tweak and exact alphabet ordering to decrypt.

Built-in alphabets are Decimal (radix 10), Alphanumeric (62 symbols: digits, uppercase, lowercase), and printable ASCII (95 symbols). Custom alphabets require 2–256 unique single-byte symbols. Every input symbol must occur in the alphabet, and the domain must contain at least 1,000,000 values. The current input cap is 4096 symbols. FF1 accepts an optional tweak up to 1024 bytes. FF3-1 requires exactly 7 tweak bytes and has a stricter radix-dependent input bound derived from its 96-bit domain limit.

For a reversible check, encrypt a valid decimal input with a test AES key using FF1, then decrypt with the same key, tweak, and alphabet. A repeated custom symbol, an input outside the alphabet, or a six-byte FF3-1 tweak is rejected. Treat output as unauthenticated until independently validated.
