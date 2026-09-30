# Caracterización previa de Encargo 42

Producción intacta respecto a 08ec1be. Fixture saved-session-no-aad.json generado por prepareForStorage del código antiguo, con contraseña y valores inventados. La prueba definitiva lee el recurso; nunca lo regenera.

Comando: `mvn -o -q -Dtest=SavedSessionAadCharacterizationTest test`

Salida Surefire:

```text
Test set: com.cryptocarver.model.SavedSessionAadCharacterizationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.848 s
```

Los cuatro comportamientos son recuperación completa, contraseña incorrecta, fixture antiguo y transplantedProtectedFieldsDecryptWithoutBinding_currentBehavior. Este último confirma el defecto: un destinatario con otro id y operation recupera secretos y rastro del donante sin error.

La primera ejecución generó el fixture y obtuvo 3 éxitos y 1 fallo: comparaba el rastro del fixture con otro rastro recién creado, cuyos UUID y timestamps son aleatorios. Se corrigió esa comparación por identidad fija, contenido inventado y verificación de la cadena de hashes. No se cambió producción ni se debilitó la prueba de ida y vuelta completa, que compara el mismo original. El generador temporal fue retirado antes del commit.

Después de introducir AAD, el test de trasplante se actualizará a rechazo, porque ese es precisamente el defecto que corrige el encargo. El resto seguirá comprobando los mismos contratos.
