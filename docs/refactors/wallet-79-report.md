# Encargo 79 — informe de cierre por parada en fase 0

Rama: `codex/wallet-1`. Worktree: `/Users/feliperodriguezfonte/dev/CryptoCarver-wallet-1`, creado desde `eb3d7448`. No se ha compilado en el repositorio principal ni ejecutado más de un Maven simultáneamente.

**Resultado: auditoría de privacidad fallida; refactor detenido conforme a la regla de fase 0.** Un SD-JWT firmado que lleva accidentalmente un JWK privado del holder en `cnf.jwk` expone `d` al verificarlo e inspeccionarlo bajo MASKED y REDACTED. Se confirma en controles FXML desplegados, resultado del shell, visor expandido, Shelf, historial y su fichero JSON. Inspector y barra de estado no lo muestran en estos casos. No se corrige el defecto.

Mapa: [wallet-0-map.md](wallet-0-map.md). Reproducción, superficies exactas y límites: [wallet-0-characterization-failures.md](wallet-0-characterization-failures.md).

## Líneas y alcance

| Archivo de producción | Antes | Después |
|---|---:|---:|
| WalletController.java | 971 | 971 |
| WalletSdJwtCoordinator.java | No existe | No creado |
| WalletMdocCoordinator.java | No existe | No creado |
| WalletStatusListCoordinator.java | No existe | No creado |

Se añade WalletPrivacyCharacterizationUITest (201 líneas). Ningún archivo de producción cambia. Se conservan los 25 manejadores y todos los fx:id. eIDAS, Trusted Lists, CBOR, SCA, OID4VP y AdES permanecen en el controlador.

## Validación

Auditoría dirigida: **1 XML / 9 pruebas / 4 fallos / 0 errores / 0 omitidas / exit 1**. La matriz ordinaria ejecuta las diez operaciones en cada uno de los tres perfiles y pasa. Los casos FULL_LAB de informes con JWK privado conservan su salida y pasan. Los cuatro casos MASKED/REDACTED de Verify/Inspect fallan por la exposición documentada. Ningún test resuelve URLs; Status List usa tokens generados localmente.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Resultado |
|---|---|---|---|---|---|---|---|---|
| 1 SD-JWT | G1 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 1 SD-JWT | G2 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 1 SD-JWT | G3 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 2 mdoc | G1 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 2 mdoc | G2 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 2 mdoc | G3 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 3 Status List | G1 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 3 Status List | G2 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |
| 3 Status List | G3 | — | — | — | — | — | — | No ejecutada: parada en fase 0 |

No se cuentan XML anteriores como puertas ni se invoca la excepción de ExpandedViewerLifecycleUITest. No hay extracción que retirar. No se han fijado transcripciones de fases 1–3. No se reclama validación completa de privacidad ni ejecución en Linux/Java 17: Maven/Surefire usó OpenJDK 25 en macOS, con release de compilación 17.

## Contratos, higiene y hallazgos

Tests existentes modificados: **ninguno**. No hay reasignación de propietarios de claves porque no se inicia extracción; los literales module.wallet.* conservan su propietario actual. SpecializedFeedbackHeadlessTest no tiene asignación Wallet; la búsqueda de contratos y los contratos FXML/recetas se documentan en el mapa. No se ejecutan contratos preparatorios para una extracción que no comienza.

Comandos de higiene del job quality-gates ejecutados: **estilos inline FXML 0; emojis 325 de 325**. `git diff --check` pasa. No se añaden imágenes, .local.md, DMG, ejecutables ni material real. Settings, Shelf e historial se restauran por la fixture y el aislamiento adicional del historial legado.

Hallazgos sin corregir: fuga del JWK privado detallada arriba; advertencia preexistente `Missing localization key: module.process.category.wallet / eidas (locale=en)`. Rechazos de material privado en campos públicos, sesiones y exportaciones distintas del historial quedan sin certificar tras la parada.

## Commits

- `f530770 — docs(wallet): map phase zero privacy surfaces and handlers`
- `b8ba676 — test(wallet): reproduce private holder JWK exposure in live UI`
- `HEAD — docs(wallet): report phase zero privacy stop for assignment 79` (este informe).

La rama queda sin cambios pendientes tras guardar este informe; el test de reproducción conserva intencionadamente sus cuatro aserciones fallidas.
