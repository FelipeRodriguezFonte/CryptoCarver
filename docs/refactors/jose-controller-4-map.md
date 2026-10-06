# JOSEController — fase 4: inspector y arranque

Base de esta fase: `1251` líneas y `102` métodos según `python3 docs/refactors/jose_method_map.py`; resultado: `1063` líneas y `93` métodos. Rama `codex/jose-controller-2`, base original `bcb97fb`.

## Inspector

Destino: `JoseInspectorCoordinator`, sin referencia ni captura del controlador. El inspector es stateless: recibe el `TextFlow` por argumento y no accede a controles propios, estado compartido ni estado de operación; por ello no necesita `View` ni `Supplier<StatusReporter>`. El API público de `JOSEController.inspectToken(String, TextFlow)` permanece y delega en una línea. El handler FXML, el visor expandido, la copia, `getInspectorReportText` y el borrado siguen como cableado de vista.

| Método trasladado | Entradas/estado vivo | Efecto |
|---|---|---|
| `inspectToken`, `inspectTokenRecursive` | Token y `TextFlow` por argumento; profundidad local | Vacía y renderiza el flow; reconoce JWS/JWE Compact, JSON Flattened/General, tokens anidados y `cty=JWT`. No descifra JWE ni publica resultados. |
| `inspectJsonSerialization` | JSON, flow, profundidad y prefijo por argumento | Presenta headers, payload/ciphertext, recipients y firmas. |
| `addSection`, `addOptionalSection`, `addJsonMember`, `prettyJson`, `addText`, `bytesToHex` | Argumentos y flow | Helpers de formato/decodificación y tipografía JavaFX; excepciones visibles solo en el inspector. |
| `handleInspectToken` y operaciones de lectura/copia/expansión | Campos FXML | Permanecen en `JOSEController`; la API pública `inspectToken` delega. |

El inspector no escribe historial, Shelf, estado, telemetría, `AppSettings`, claves cargadas ni `OperationResult`. No se cambiaron `crypto/`, `pom.xml`, `ModernMainController`, `UiStateSnapshot`, `StatusReporter` ni `OperationResult`.

## Arranque y navegación

`initialize` (154 líneas) mezcla el enlace de `ModuleI18n`, listener de locale, valores iniciales PBES2, población de combos/tablas, listener del tipo JWK, `selectFirst`, listeners de avisos de seguridad y plantilla de acciones. Los listeners se instalan intercalados con la población de controles; extraer una parte exigiría partir la secuencia o inyectar callbacks y puede cambiar el orden de eventos. Siguiendo la regla de no extraer ante duda, `JoseControlsInitializer` no se creó y `initialize` se conserva íntegro. Permanecen el orden de elementos, marcas de algoritmos inseguros y avisos del encargo 70.

`showSection` (58 líneas) altera `managed`/`visible` de secciones FXML y participa en navegación y en el flujo global de importación. Se conserva en el controlador junto con los listeners y el cableado de eventos; no se demostró una frontera segura para moverlo.

## Caracterización

`JoseInspectorCharacterizationUITest` cubre JWT Compact; JWS Compact y General JSON; JWE Compact, Flattened y General JSON; JWT anidado; entradas raw/malformadas; y los tres perfiles FULL_LAB/MASKED/REDACTED mediante el fixture común. Usa claves/payload inventados. La transcripción guarda marcadores estructurales y categorías de privacidad; normaliza campos aleatorios y omite tokens, claves, firmas y texto del proveedor.

Digest portable fijado y verificado antes y después de la extracción: `89c07b84cea4a9a478881fd369a1b47796a4d7a5d2008165cddf341518c261da`.

Durante la captura inicial se dejó temporalmente `DIGEST_PENDING` para obtener la transcripción canónica; la aserción falló con el SHA anterior, se fijó y la prueba aislada pasó antes de extraer. No fue un fallo funcional. `JOSEControllerInspectorTest` y las caracterizaciones previas no se modificaron y pasan en las puertas.

Los avisos de seguridad en inglés/español del encargo 70 permanecen cubiertos por `JoseCapabilitiesCharacterizationUITest`; esta prueba añade marcadores de idioma sin duplicar ni debilitar esa aserción.

## Puertas de control

Se borró `target/surefire-reports` antes de cada puerta y los recuentos se calcularon solo con los XML generados por esa ejecución.

| Comando | XML | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| `mvn -o -q test -Plow-cpu` | 438 | 2897 | 0 | 0 | 1 |
| `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 120 | 536 | 0 | 0 | 0 |
| `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | 120 | 536 | 0 | 0 | 0 |

En esta ejecución no falló `ExpandedViewerLifecycleUITest`. El informe de fase 2 documenta sus fallos previos reproducibles en Mac/JDK 25 sobre `main`; no se atribuyen a esta rama.
