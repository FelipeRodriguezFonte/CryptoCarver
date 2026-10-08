# Regla A — reproducción mdoc Inspect

La G1 intermedia posterior a B (`26a1a17`, 459 XML / 2957 pruebas / 8 fallos / 0 errores / 1 omitida / exit 1) se acepta expresamente en la tercera continuación, **solo esa ejecución**. Casos rojos de WalletAdditionalPrivacyCharacterizationUITest:

1. MASKED mdoc JWK privado.
2. MASKED mdoc PEM privado.
3. MASKED Status List JWK privado.
4. MASKED Status List PEM privado.
5. REDACTED mdoc JWK privado.
6. REDACTED mdoc PEM privado.
7. REDACTED Status List JWK privado.
8. REDACTED Status List PEM privado.

Para mdoc, el documento válido local lleva un elemento lab_private_fixture con un JWK privado o PEM privado. handleMdocInspect escribe el informe en mdocVerifyOutputArea. En los cuatro casos restringidos el control contiene el secreto; para JWK también lo muestran resultado del shell, Shelf y visor expandido. Los FULL_LAB pasan. Prueba existente: WalletAdditionalPrivacyCharacterizationUITest, casos [5], [6], [9], [10]; sus aserciones no se modifican. La evidencia está en la G1 aceptada y wallet-0-additional-characterization-failures.md.

Se autoriza corregir este manejador según A. runMdocReport es compartido con Verify: ambos publican informes derivados de la misma estructura, por lo que la política común se aplica a ambos. Se mantienen verificaciones, estados, errores, FXML y salida FULL_LAB.
