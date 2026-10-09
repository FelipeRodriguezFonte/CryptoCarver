# Encargo 80 — resto de WalletController

Hecho por el revisor a petición del usuario, en el worktree `CryptoCarver-wallet-2`, rama `codex/wallet-2`. Base: `e4dc38e` (`main` con los encargos 79 y 81 fusionados y CI en verde).

## Resultado

`WalletController` pasa de **793 a 531 líneas**. Conserva los campos FXML, `initialize`, `showSection`, `handleClear`, `handleLoadExample` y los delegados de una línea. Clases nuevas:

| Clase | Líneas | Contenido |
|---|---:|---|
| `WalletTrustCoordinator` | 135 | certificado eIDAS, Trusted List (inspect, verify, find) y Trusted Entity List JSON |
| `WalletCborCoordinator` | 68 | CBOR inspect, CBOR a JSON y JSON a CBOR |
| `WalletScaCoordinator` | 149 | datos de transacción SCA, verificación SCA, OpenID4VP y validación AdES |
| `WalletCoordinatorBase` | 105 | auxiliares de entrada y publicación compartidos por los tres |
| `WalletPrivateMaterialPolicy` | 50 | regla de material privado en informes |

El código de los manejadores se movió literal; solo cambia el acceso a los controles (por `View`) y al reporter (por `Supplier<StatusReporter>`). No se tocó el FXML ni ningún `fx:id`.

## Fase 0 — auditoría y corrección de privacidad

`WalletRemainingPrivacyAuditUITest`, con el shell y el FXML reales, pega en cada manejador una estructura con un JWK EC privado inventado. Sobre `e4dc38e`, bajo MASKED y REDACTED:

- CBOR Inspect, CBOR to JSON, JSON to CBOR y Trusted Entity List JSON mostraban el escalar privado en el área de resultado, el resultado del shell, el Shelf y el visor expandido.
- OpenID4VP y SCA Transaction Data mostraban el informe, sin el escalar literal.
- Inspector, barra de estado e `history.json` no lo contenían.

Detalle en `wallet-80-phase0-reproduction.md`. Corrección (`1b2ac58`), autorizada por la regla fija del encargo: `WalletPrivateMaterialPolicy.forDisplay` en los trece manejadores. Fuera de FULL_LAB, si el informe o su fuente llevan material privado, el área de resultado y lo publicado muestran `module.wallet.privateJwkHidden`; bajo FULL_LAB no cambia nada. El detector no se amplió. En SCA Build, una entrada oculta no se copia al panel de verificación.

Con operación normal y claves inventadas, las tres caracterizaciones recorren los tres perfiles sin exponer material: las claves que piden estos manejadores son públicas.

## Caracterizaciones

| Fase | Test | SHA-256 | Verificada sin extraer |
|---|---|---|---|
| 4 | `WalletTrustCharacterizationUITest` | `a8e749cfa04a0372d6b8af2762cddac2c56bc7b805a30fb7da787937e049fc07` | sí, sobre `1b2ac58` |
| 5 | `WalletCborCharacterizationUITest` | `a2e1669cb0691e6038045a4e1792c422f8c6cb49312fe269bb9cea0b16e9a95a` | sí, sobre `1b2ac58` |
| 6 | `WalletScaCharacterizationUITest` | `6922b34954cd3c9a884f92653ddd413ef4f60d5abaa4d0739e690ca626403caf` | sí, sobre `1b2ac58` |

Las tres se escribieron y fijaron sobre el controlador con la corrección de la fase 0 y sin ninguna extracción, y pasan sin cambios después de las tres. Inglés y español, tres perfiles. Mapas: `wallet-4-map.md`, `wallet-5-map.md`, `wallet-6-map.md`.

## Puertas

Ejecutadas en un worktree desligado sobre el commit exacto de cada fase, borrando `target/surefire-reports` antes de cada una y contando solo los XML de esa ejecución. Un único Maven a la vez. macOS, Maven con OpenJDK 25.

| Fase | Commit | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---|---|---:|---:|---:|---:|---:|---:|
| 0 | `1b2ac58` | G1 | 470 | 2983 | 0 | 0 | 1 | 0 |
| 0 | `1b2ac58` | G2 | 146 | 596 | 0 | 0 | 0 | 0 |
| 0 | `1b2ac58` | G3 | 146 | 596 | 0 | 0 | 0 | 0 |
| 4 | `b7414ea` | G1 | 471 | 2984 | 0 | 0 | 1 | 0 |
| 4 | `b7414ea` | G2 | 147 | 597 | 0 | 0 | 0 | 0 |
| 4 | `b7414ea` | G3 | 147 | 597 | 0 | 0 | 0 | 0 |
| 5 | `729cdc9` | G1 | 472 | 2985 | 1 | 0 | 1 | 1 |
| 5 | `729cdc9` | G1, repetida | 472 | 2985 | 3 | 0 | 1 | 1 |
| 5 | `729cdc9` | G2 | 148 | 598 | 0 | 0 | 0 | 0 |
| 5 | `729cdc9` | G3 | 148 | 598 | 0 | 0 | 0 | 0 |
| 6 | `0e324fe` | G1 | 473 | 2986 | 0 | 0 | 1 | 0 |
| 6 | `0e324fe` | G2 | 149 | 599 | 0 | 0 | 0 | 0 |
| 6 | `0e324fe` | G3 | 149 | 599 | 3 | 0 | 0 | 1 |
| 6 | `0e324fe` | G3, repetida | 149 | 599 | 0 | 0 | 0 | 0 |

Diez de las doce pasan limpias a la primera. Las dos restantes:

- **Fase 6, G3:** los tres tests de `ExpandedViewerLifecycleUITest` (GC). La repetición de la puerta pasa limpia; aceptada por la regla de la excepción de GC.
- **Fase 5, G1:** en la primera ejecución falló un caso de `WalletAdditionalPrivacyCharacterizationUITest` (test del encargo 79, sobre mdoc y Status List, que la fase 5 no toca). **No se conserva el mensaje de ese fallo** porque el log se sobrescribió con la puerta siguiente. No se reprodujo: la clase aislada pasó 5 de 5 (12 casos cada vez) sobre `729cdc9`, pasó en G2 y G3 de ese mismo commit y en las once puertas restantes. En la repetición de G1 ese test pasó y los únicos fallos fueron los tres de GC; la clase de GC aislada pasó después 2 de 2 sobre `729cdc9` y 2 de 2 sobre su base `b7414ea`, alternando. La puerta se acepta por la regla de GC, y el fallo aislado del test de Wallet queda anotado como intermitente sin causa identificada. G1 del commit siguiente (`0e324fe`), que contiene todo el código de la fase 5, pasó limpia.

## Tests existentes modificados

Ninguno. Solo se añaden tests. No hubo propietarios de claves que reasignar: ningún test de presencia en fuente asigna claves `module.wallet.*` a `WalletController`. Antes de las puertas se ejecutaron solos `WalletControllerDefaultsTest`, `ModernMainControllerFxmlStaticTest` y `SpecializedFeedbackHeadlessTest` (3 informes / 36 pruebas / 0 fallos) y los nueve tests UI de Wallet (28 pruebas / 0 fallos).

## Hallazgos no corregidos

- `handleTrustedListVerify` publica el estado "Verified" aunque la firma no sea válida o no exista.
- Una clave privada en CBOR con etiquetas enteras (COSE_Key, parámetro `-4`) no tiene la forma que reconoce `PrivateKeyMaterialDetector` y se sigue mostrando fuera de FULL_LAB. Corregirlo exige ampliar el detector.
- Intermitente sin causa identificada en `WalletAdditionalPrivacyCharacterizationUITest` (un fallo en diecinueve ejecuciones de la clase: catorce puertas y cinco aisladas).
- Sin cobertura: Trusted List y Trusted Entity List firmadas, búsqueda de certificado en la lista JSON, y documentos CAdES, PAdES y ASiC en la validación AdES (solo XAdES).
- Advertencia de traducción preexistente `module.process.category.wallet / eidas`.

## Higiene

Estilos en línea en FXML: **0**. Emojis: **325 de 325**. Sin cambios en `crypto/`, `pom.xml`, `ModernMainController`, `UiStateSnapshot`, `StatusReporter`, `OperationResult` ni en ficheros de idioma. Todo el cambio está en `ui/` y `docs/refactors`.

## Commits

```text
4d811f9 test(wallet): reproduce pasted private JWK exposure in remaining handlers
1b2ac58 fix(wallet): apply private material policy to remaining report handlers
ada2aa6 docs(wallet): map trust coordinator extraction
83585aa test(wallet): characterize eIDAS certificate and trusted list handlers
b7414ea refactor(wallet): extract eIDAS and trusted list coordinator
aaaf2c8 docs(wallet): map CBOR coordinator extraction
89eae6e test(wallet): characterize CBOR inspection and conversion handlers
729cdc9 refactor(wallet): extract CBOR coordinator
dbb6ce6 docs(wallet): map SCA, OpenID4VP and AdES coordinator extraction
4ad215a test(wallet): characterize SCA, OpenID4VP and AdES handlers
0e324fe refactor(wallet): extract SCA, OpenID4VP and AdES coordinator
```
