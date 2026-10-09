# Encargo 80, fase 0 — reproducción antes de corregir

`WalletRemainingPrivacyAuditUITest` sobre `e4dc38e`, sin tocar producción. Shell y FXML reales, historial y Shelf aislados, un JWK EC privado inventado por caso. Comando:

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dtest=WalletRemainingPrivacyAuditUITest
```

1 informe / 1 prueba / 1 fallo / 0 errores / exit 1. El fallo agrupa estas violaciones, idénticas bajo MASKED y REDACTED:

| Caso | Área de resultado | Resultado del shell | Shelf | Visor expandido |
|---|---|---|---|---|
| CBOR Inspect | expone el escalar privado | sí | sí | sí |
| CBOR to JSON | expone el escalar privado | sí | sí | sí |
| JSON to CBOR | lo expone dentro del hexadecimal | sí | sí | sí |
| Trusted Entity List JSON | expone el escalar privado | sí | sí | sí |
| OpenID4VP Inspect | muestra el informe; el escalar no aparece literal | no | no | no |
| SCA Transaction Data | muestra la entrada en base64url; el escalar no aparece literal | no | no | no |

Inspector, barra de estado e `history.json` no contienen el escalar en ningún caso. Bajo FULL_LAB los seis casos conservan su resultado original.

Dos ajustes de la propia prueba antes de esta ejecución, no de producción: el primer JSON de Trusted Entity List no cumplía el esquema TS 119 602 (faltaba `TEAddress`, y `OtherIds` debe ser una lista de cadenas).
