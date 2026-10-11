# Check digits

Calculate takes the base without its final digit (at least one digit) and displays the check digit plus the complete number. Validate takes the complete number (at least two digits: base and check) and checks its final digit. Both require ASCII digits `0`–`9`; letters, spaces and separators are rejected. Values remain text, preserving leading zeros: `00123` is not converted to integer `123`.

Rules exposed by the selector:

- **Luhn (Mod 10):** doubles alternating base digits starting from the right, subtracts 9 from each result above 9, then chooses the digit that completes a multiple of 10. `7992739871` → `79927398713`.
- **AMEX SE (Luhn/Mod 10):** in this app the label uses exactly the same Luhn calculation; it does not check issuer, card range or AMEX-specific validity. Base `572` produces `5728`.
- **Verhoeff:** uses multiplication, position-dependent permutation and inverse tables; the implementation detects every single-digit error and most transpositions. Base `572` produces `5727`.
- **Damm:** folds digits left-to-right through a non-commutative quasigroup table; the implementation detects every single-digit error and every adjacent transposition. Base `572` produces `5724`.

The digit helps detect some transcription errors. It does not establish identity, issuer or authenticity. Code references: `CheckDigitCoordinator`, `CheckDigitCalculator` and `generic.fxml`; no external numbering-system rule is validated.
