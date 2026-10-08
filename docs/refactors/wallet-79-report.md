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

## Validación de la parada original

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
- `a9f81e7 — docs(wallet): report phase zero privacy stop for assignment 79` (informe original).

La rama queda sin cambios pendientes tras guardar este informe; el test de reproducción conserva intencionadamente sus cuatro aserciones fallidas.


## Continuación: corrección autorizada y segunda parada

Se conserva arriba el registro de la primera parada. Esta continuación mantiene el mismo worktree y rama, sin compilar en el principal y con un solo Maven activo. **Estado actual: parado por nuevas fugas confirmadas en mdoc Inspect y Status List Describe.** No se corrigen esos módulos y no comienza ninguna extracción.

El detector containsPrivateMaterial se extrae a PrivateKeyMaterialDetector sin cambiar su lógica; se ha comparado el cuerpo literalmente con el original. JoseInspectorCoordinator delega. Los tests UI JOSE seleccionados con `-Dtest=Jose*` pasan sin cambios.

SD-JWT Verify e Inspect ejecutan la misma operación y, en MASKED/REDACTED, muestran y publican `module.wallet.privateJwkHidden` si hay material privado en informe/token o sus estructuras decodificadas. El aviso tiene traducciones EN/ES y fallback con el sentido de module.jose.jwkPrivateHidden; no lleva símbolo ni añade emojis. FULL_LAB conserva el informe original. Los estados y el camino de fallo no cambian. El literal nuevo sigue siendo propiedad de WalletController; no hay coordinador extraído.

**La reproducción original aún no está verde.** Sin tocar su archivo ni sus aserciones, los cuatro fallos restantes se limitan al historial: UiStateSnapshot captura `WalletController.sdJwtClaimsArea` en la receta de entrada, incluida la entrada anterior de Issue. Cambiar el informe publicado protege las demás superficies, pero no elimina esa ruta independiente. No se borra historial ni se vacían editores para hacer pasar el test. Resolver esa receta requiere ampliar el alcance de corrección respecto de los dos informes autorizados y respetar los archivos protegidos. Detalle: [wallet-0-correction-validation.md](wallet-0-correction-validation.md).

La ampliación usa documentos mdoc válidos y Status List Tokens con firma verificada, generados localmente, que contienen un JWK privado o un PEM privado. En ambos módulos y perfiles restringidos se confirma exposición en los TextArea reales desplegados. El JWK también llega al resultado del shell, Shelf y visor expandido. Los cuatro casos FULL_LAB pasan. No se atribuye a estos dos casos una fuga de historial que no se ha observado. Detalle y fx:id: [wallet-0-additional-characterization-failures.md](wallet-0-additional-characterization-failures.md).

### Ejecuciones dirigidas de la continuación

Cada ejecución borra previamente target/surefire-reports y cuenta solo sus XML; estas ejecuciones no se presentan como puertas completas.

| Ejecución confirmatoria | XML | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---:|---:|---:|---:|---:|---:|
| JOSE UI, detector compartido | 13 | 17 | 0 | 0 | 0 | 0 |
| Reproducción SD-JWT original tras corrección de salida | 1 | 9 | 4 | 0 | 0 | 1 |
| Auditoría ampliada mdoc/Status List | 1 | 12 | 8 | 0 | 0 | 1 |

No se relajan ni modifican tests existentes. Los dos FULL_LAB SD-JWT originales siguen pasando. Se añade únicamente WalletAdditionalPrivacyCharacterizationUITest para la ampliación. La primera preparación del test adicional falló antes de alcanzar Status List por un mapa inmutable del Builder; se corrigió la fixture y se repitió, sin alterar aserciones. Se registran ambos recuentos en el documento de fallos.

### Las doce puertas de la continuación

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Resultado |
|---|---|---|---|---|---|---|---|---|
| 0 Corrección | G1 | — | — | — | — | — | — | No ejecutada: parada por auditoría ampliada |
| 0 Corrección | G2 | — | — | — | — | — | — | No ejecutada: parada por auditoría ampliada |
| 0 Corrección | G3 | — | — | — | — | — | — | No ejecutada: parada por auditoría ampliada |
| 1 SD-JWT | G1 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 1 SD-JWT | G2 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 1 SD-JWT | G3 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 2 mdoc | G1 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 2 mdoc | G2 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 2 mdoc | G3 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 3 Status List | G1 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 3 Status List | G2 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |
| 3 Status List | G3 | — | — | — | — | — | — | No ejecutada: fase 0 detenida |

Se aplica la orden de parar y documentar si la ampliación expone material. No se aplica la excepción de GC ni hay extracción que retirar. No se fijan caracterizaciones de fases 1–3 sobre una fase 0 que sigue fallando.

### Líneas, higiene y alcance actuales

WalletController: **971 líneas originales → 990 tras la corrección autorizada**, sin extracción. Los tres coordinadores Wallet siguen sin crearse. PrivateKeyMaterialDetector es una utilidad nueva de 37 líneas.

Se ejecutó literalmente el bloque de higiene de quality-gates de .github/workflows/ui-tests.yml: **inline=0, emoji=325**. git diff --check pasa. Se comprobó que crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter y OperationResult permanecen intactos. La reproducción original y todos los demás tests existentes también permanecen intactos. Settings, Shelf e historial se restauran. Sin red, imágenes, .local.md, DMG, ejecutables ni material real.

Hallazgos pendientes: recetas de historial con claims privados, fugas mdoc/Status List reproducidas y advertencia de traducción preexistente. No se afirma validación Linux/Java 17: Maven/Surefire usó OpenJDK 25 en macOS con release de compilación 17.

### Commits de la continuación

- `3baa72e — refactor(ui): share JOSE private key material detector unchanged`
- `50a267d — fix(wallet): hide private SD-JWT verify and inspect reports`
- `9170be0 — docs(wallet): record corrected outputs and remaining history recipe leak`
- `6d048a1 — test(wallet): reproduce mdoc and status list private material leaks`
- `HEAD — docs(wallet): report authorized correction and second privacy stop` (actualización de este informe).

Rama sin cambios pendientes al cerrar; las reproducciones conservan intencionadamente sus fallos pendientes.
