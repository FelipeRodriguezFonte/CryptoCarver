# UUID v4

Generate creates a lowercase canonical string with 36 characters and 8-4-4-4-12 groups. Its 32 Hex digits encode 128 bits, including reserved version/variant bits; the third group starts with `4` and the fourth with `8`, `9`, `a` or `b`. Copy transfers the visible value to the clipboard and does not generate another.

Check the result with `UUID.fromString`, version 4 and the RFC 4122/9562 variant. A UUID is a random identifier, not a secret key with 128 bits of entropy. Context shares only the action/field; it never copies the value. Implementation: `UUIDGenerator`, `GenericController` and `generic.fxml`.
