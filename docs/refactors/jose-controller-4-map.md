# JOSEController — fase 4: inspector y arranque

Base: `1251` líneas, `102` métodos según `python3 docs/refactors/jose_method_map.py`. Sigue a `jose-controller-3-map.md`; rama `codex/jose-controller-2`, base `bcb97fb`.

## Inspector

Destino: `JoseInspectorCoordinator`, `record View(TextFlow inspectorOutputFlow)` y sin referencia/captura del controlador. Los controles se obtienen perezosamente; el coordinador no publica resultados ni conserva tokens.

| Método | Estado UI / datos | Efecto |
|---|---|---|
| `inspectToken` (4 líneas) | Entrada `token`, `TextFlow` por argumento | Limpia el flow y llama parser recursivo. API pública se mantiene como delegado. |
| `inspectTokenRecursive` (52) | `token`, profundidad, `TextFlow` por argumentos | Distingue Compact JWS/JWE y JSON; descodifica segmentos, detecta payload JWT anidado y nota `cty=JWT`. Sin estado ni resultados publicados. |
| `inspectJsonSerialization` (51) | JSON, flow, profundidad/prefix por argumento | Presenta JWE/JWS Flattened y General; lee miembros de JSON solamente. |
| `addSection` (42), `addOptionalSection`, `addJsonMember`, `prettyJson`, `addText`, `bytesToHex` | Argumentos y flow | Formato/colores/tipografía JavaFX; mensajes de parsing visibles solo en el inspector. Helpers privados movidos junto con el propietario. |
| `handleInspectToken` | Lee `inspectorInputArea`, modifica `inspectorOutputFlow`, invoca validación vacía | Se conserva en el controlador como handler FXML y delega inspección. |
| `handleClearInspector`, `getInspectorReportText`, copia/visor expandido | Controlan el ciclo de vida de vista y lectura externa | Se conservan como cableado; no se mueve navegación ni el visor. |

El inspector no escribe historial, Shelf, status ni telemetría. La rutina solo decodifica segmentos visibles; no descifra JWE. Los tests de capacidades existentes mantienen cobertura de avisos del encargo 70 y permanecen intactos. Ningún estado AppSettings, clave cargada o `StatusReporter` se lee/escribe.

## Arranque y navegación

`initialize` (154 líneas) mezcla enlace `ModuleI18n`, registro del listener de cambio de locale, inicialización de campos PBES2, población de selectores/tablas, listener `selectedItemProperty` para tipo JWK, `setOnAction` de templates y listeners que recalculan avisos de seguridad. El listener de tipo JWK se instala entre el llenado de su selector y `selectFirst`; los listeners de avisos se instalan después de poblar todos los combos. Extraer la población aislada exigiría cortar el flujo en varios puntos e inyectar callbacks que alteran el orden del listener; se considera frágil. Por la regla explícita ante la duda, `JoseControlsInitializer` no se crea y `initialize` permanece completo.

`showSection` (58 líneas) solo cambia `managed`/`visible` pero está acoplado a las secciones FXML y se invoca desde navegación y flujo global de importación; queda en el controlador. No se cambia cableado/listeners. La API pública no varía.

## Caracterización del inspector

Antes de extraer: JWT Compact, JWS/JWE Compact y Flattened/General JSON, JWT anidado, malformados y avisos del encargo 70; perfiles FULL_LAB/MASKED/REDACTED en fixture común. Transcripción SHA-256 solo guarda marcadores de estructura/categoría, no tokens, firmas, claves aleatorias, nonce, texto arbitrario ni mensajes de excepción. Claves y payloads inventados. Los tests `JOSEControllerInspectorTest`, `JoseCapabilitiesCharacterizationUITest` y `JoseJwkCharacterizationUITest` no se debilitan.

Digests base existentes, fijados en fases anteriores y aún deben verificarse al cierre: JWT/JWS `526ed1435a5950126aae7065abbaeee3b6c9bb81c8cee77c3a4841e7e599537e`; crit real `0776e413f48a43f8d7afa8f0e4dadee462ef2aae969b4fb41d71e268fc7b499a`; JWE serializaciones/privacidad `898aa2dc9da06352c580966e0a54dee58d8db86d94ea2bcc3febe5beb7d42401`; JWE avisos/CEK/errores `784d6a61e011dee39d8c2c5f6798bd4a29484f2fbc86d8f041d4e39ed69272fd`; capacidades existentes `bc36301eed279b7a7c7a83ac29214da2dcd1fc9e79eb3a52e034261dea5e64db`; JWK `315e6810e2bc477b3f4de37ff4bc6c1b81acc057f1b4a5bbebb5f3e8a88c9d1a`.
