# Random bytes

Enter an integer from 1 through 1024 bytes inclusive. Bytes are generated with `SecureRandom`; output changes on every run. Hexadecimal uses 2N characters; Binary uses 8N bits separated by spaces; Decimal displays N unsigned values 000–255; Base64 uses standard groups with a length divisible by four. Structural example: 16 bytes produce 32 Hex characters, and decoding the output recovers 16 bytes. Zero, 1025, decimals and non-integer text are rejected and preserve the previous output.

The selector sets the format for the next generation. Changing it does not transform the output already shown; pressing Generate draws another random byte sequence in the selected format. This is not a UUID or a guarantee that the bytes are suitable as a key without considering their use. Implementation: `GenericController.handleGenerateRandom`, `OperationFormatRegistry` and `generic.fxml`.
