# Modular Arithmetic

Standard operands are parsed as Hex integers; the parser removes every character outside `[0-9A-F]`, so do not use separators to express a sign. Hex XOR requires two Hex operands. Decimal XOR requires decimal digits in A and B, ignores modulus and displays a Hex result.

Addition, subtraction, multiplication and exponentiation require A, B and m>1; exponent B must be non-negative. Additive inverse and reciprocal require A and m>1. A reciprocal exists only when `gcd(A,m)=1`; failure explains this condition. GCD, LCM and Extended GCD require A/B and do not use modulus; Extended GCD includes Bézout coefficients. Example: A=`03`, B=`04`, m=`0B`: `(A+B) mod m` produces Hex `7` (decimal 7). Reciprocal of 6 modulo 9 fails because gcd=3.

Output includes Hex, decimal, binary and bit length, with extra text depending on operation. Implementation: `ModularArithmeticCoordinator`, `ModularArithmetic` and `generic.fxml`.
