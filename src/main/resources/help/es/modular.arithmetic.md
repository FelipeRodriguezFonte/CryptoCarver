# Aritmética modular

Los operandos estándar se interpretan como enteros Hex; el parser elimina cualquier carácter que no sea `[0-9A-F]`, así que no use separadores para indicar signo. XOR Hex requiere dos operandos Hex. XOR Decimal exige dígitos decimales en A y B, ignora módulo y presenta resultado Hex.

Suma, resta, multiplicación y exponenciación requieren A, B y m>1; exponente B no negativo. Inversa aditiva y recíproco requieren A y m>1. El recíproco existe solo si `gcd(A,m)=1`; si falla, el resultado explica la condición. GCD, LCM y Extended GCD requieren A/B y no usan módulo; Extended GCD incluye coeficientes de Bézout. Ejemplo: A=`03`, B=`04`, m=`0B`: `(A+B) mod m` produce Hex `7` (decimal 7). Recíproco de 6 módulo 9 falla porque gcd=3.

La salida incluye Hex, decimal, binario y longitud de bits, además de texto extra según la operación. Implementación: `ModularArithmeticCoordinator`, `ModularArithmetic` y `generic.fxml`.
