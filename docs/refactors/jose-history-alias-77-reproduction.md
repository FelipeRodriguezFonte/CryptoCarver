# Encargo 77: reproducción antes del arreglo

Base a4287977, worktree CryptoCarver-jose-6, rama codex/jose-history-alias.

Comando: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=JoseHistoryAliasUITest`.

Resultado: 1 informe / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1.

Test: `oldQualifiedJwksRecipeRestoresContent`. La receta usa JOSEController.jwksArea con un JWK simétrico privado inventado, bajo FULL_LAB. La aserción esperaba PRIVATE_JWKS, el JSON fijo inventado del test, y obtuvo cadena vacía; salida `legacy JWKS recipe must restore the current input` seguida del expected/actual vacío. No falló JavaFX ni la compilación. Log completo local: target/jose77-reproduction.log.

Un comprobador auxiliar del mensaje XML falló por asumir un formato concreto del wrapper de aserción. Se corrigió el registro y se restauró el código base antes de comprometer este paso. El fallo de producto fue observado antes de cualquier arreglo; el test rojo y esta evidencia quedan en el primer commit, sin cambios de producción.
