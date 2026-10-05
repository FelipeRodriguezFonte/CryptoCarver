# JOSEController — fase 1

Base: `2141` líneas, `101` métodos detectados (incluye constructores; excluye record). Medido por `python3 docs/refactors/jose_method_map.py`, ignorando llaves en literales/comentarios.

Destino: `JoseJwtCoordinator` con `record View`, proveedor vivo de controles y `Supplier<StatusReporter>`. El controlador conserva API, FXML, navegación, listeners, importación y diálogos de fichero. Ningún coordinador recibe un controlador.

| Método (línea original) | Líneas | Estado FXML leído / modificado | Dependencias y efectos |
|---|---:|---|---|
| `handleApplyJWTClaims` L670 | 19 | Lee: jwtAudField, jwtPayloadArea, jwtIssField, jwtExpField, jwtSubField. Modifica: jwtPayloadArea | I18n vivo EN/ES; reloj: normalizar en digest |
| `handleVerifyDetachedJWS` L690 | 11 | Lee: detachedStatusLabel, detachedPayloadArea, detachedTokenArea, detachedVerificationKeyArea, detachedAlgoCombo, detachedSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleVerifyNestedJWT` L702 | 12 | Lee: nestedOutputArea, nestedSigningKeyArea, nestedStatusLabel, nestedPayloadOutputArea, nestedEncryptionKeyArea, nestedSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleGenerateDetachedJWS` L729 | 14 | Lee: detachedUnencodedCheck, detachedPayloadArea, detachedTokenArea, detachedSigningKeyArea, detachedAlgoCombo, detachedSerializationCombo, detachedSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleGenerateNestedJWT` L745 | 18 | Lee: nestedContentAlgoCombo, nestedCompressCheck, nestedOutputArea, nestedKeyAlgoCombo, nestedSigningKeyArea, nestedPayloadArea, nestedSignAlgoCombo, nestedEncryptionKeyArea, nestedSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleGenerateSignedJWT` L828 | 30 | Lee: jwtAlgo2Combo, jwtKeyArea2, jwsUnencodedPayloadCheck, jwtKeyArea, jwtPayloadArea, jwtProtectedHeaderArea, jwtAlgoCombo, jwsSerializationCombo, jwtOutputArea, jwtSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `handleValidateJWT` L866 | 35 | Lee: jwtDecodedPayloadArea, jwtExpectedAudField, jwtOidcStrictCheck, jwtValidateKeyArea, jwtExpectedIssField, jwtClockSkewField, jwtStatusLabel, jwtValidateTokenArea, jwtDecodedHeaderArea, jwtCheckExpiryCheck, jwtTrustHeaderKeyCheck, jwtAllowedAlgorithmsField, jwtExpectedTypeField, jwtExpectedContentTypeField, jwtExpectedNonceField, jwtAccessTokenField, jwtAuthorizationCodeField, jwtExpectedJktField, jwtExpectedX5tField, jwtUnderstoodCritField, jwtRfc9068Check, jwtIgnoreCritCheck, jwtValidateSecretFormatCombo. Modifica: salidas por argumento / retorno | I18n vivo EN/ES |
| `generateDetachedJWS` L1157 | 21 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `verifyDetachedJWS` L1179 | 15 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `generateSignedJWT` L1207 | 3 | Lee: argumentos. Modifica: salidas por argumento / retorno | servicio JOSE / lógica de transformación; ver firma para salidas por parámetro |
| `generateSignedJWT` L1211 | 30 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `generateNestedJWT` L1243 | 25 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes |
| `verifyNestedJWT` L1269 | 20 | Lee: argumentos. Modifica: salidas por argumento / retorno | reporter vivo: publicación/error/status; I18n vivo EN/ES |
| `validateJWTAdvanced` L1637 | 6 | Lee: argumentos. Modifica: salidas por argumento / retorno | servicio JOSE / lógica de transformación; ver firma para salidas por parámetro |
| `validateJWTAdvanced` L1644 | 7 | Lee: argumentos. Modifica: salidas por argumento / retorno | servicio JOSE / lógica de transformación; ver firma para salidas por parámetro |
| `validateJWTAdvanced` L1652 | 58 | Lee: jwtAcceptNoneCheck, jwtFindingsArea. Modifica: jwtFindingsArea | reporter vivo: publicación/error/status; I18n vivo EN/ES; avisos públicos no bloqueantes; reloj: normalizar en digest |

Estado común: no hay claves cargadas independientes: viven en TextArea; no se copia material a campos nuevos. AppSettings se consulta mediante las políticas existentes; el reporte publicado se entrega al shell, sin caché en coordinadores. Los helpers de validación y avisos son comunes y el reporter se resuelve en cada uso.

Lógica pura: construcción/lectura JOSE y formato con servicios existentes. UI: lectura de controles, validación, colores, publicación y diálogos. No se cambia crypto/, OperationResult, StatusReporter ni las políticas del shell.

Caracterización: claves inventadas, salidas aleatorias comprobadas semánticamente y normalizadas antes del SHA-256; errores de proveedor solo comprobados como legibles, no incluidos textualmente. EN/ES y FULL_LAB/MASKED/REDACTED. Tests existentes intactos; cualquier fallo se anota antes del arreglo en el registro de la fase.

JWT/JWS comparten firmantes, validación y controles; un solo JoseJwtCoordinator evita dividir el JWT anidado entre dos propietarios. La plantilla seleccionada en initialize mantiene su listener y orden; handleApplyJWTClaims sí se extrae.

Digest JWT/JWS fijado y verificado antes de extracción: `526ed1435a5950126aae7065abbaeee3b6c9bb81c8cee77c3a4841e7e599537e`. Las nuevas comprobaciones de algoritmos permitidos/RFC9068/OIDC/crit preservan el comportamiento observado; una prohibición de algoritmo impide verificar (rojo), no es solo una advertencia (naranja).

Extracción: JOSEController pasa de 2141 a 1828 líneas. Se extraen 16 métodos (incluye sobrecargas), más helpers de avisos compartidos. Los proveedores de View/reporter son vivos y el getter del coordinador es perezoso: funciona tanto antes como después de FXML/setReporter. Los listeners de advertencias y su orden siguen en initialize. Las claves literales de los campos de validación permanecen intactas. Digest y cuatro pruebas JOSE originales pasan tras extracción.
