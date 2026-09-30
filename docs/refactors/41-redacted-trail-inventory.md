# Encargo 41 — inventario previo (base b6704cb)

Inventario obtenido con `rg` antes de modificar producción.

## Campos y clasificación actual

`SessionOperationStep` conserva todos los valores en claro. La entrada y los parámetros se tratan como SECRET en `SessionTrailViewFormatter`; la salida y la salida enriquecida tienen clasificación independiente (PUBLIC por defecto). Cada `OperationDetail` tiene PUBLIC, SENSITIVE o SECRET (null se normaliza a PUBLIC).

| Campos | Clasificación / riesgo actual |
| --- | --- |
| id, timestamp | Metadatos sin clasificación |
| title, tags | Texto libre del usuario; puede contener secretos, sin clasificación |
| operation, status | Texto de la operación sin clasificación; status puede incluir valores |
| inputPresent, outputPresent | Presencia, sin clasificación |
| inputLength, outputLength | Longitudes; derivadas del material |
| inputHex, inputText | Entrada, tratada como SECRET |
| outputHex, outputText | outputClassification |
| inputFingerprint, outputFingerprint | Huellas del material, potencialmente sensibles |
| enrichedOutput, enrichedOutputFingerprint | enrichedOutputClassification / huella derivada |
| outputClassification, enrichedOutputClassification | Metadatos PUBLIC/SENSITIVE/SECRET |
| details | Nombre, valor, clasificación, multiline y format; valor según clasificación |
| parameters | Claves y valores capturados en claro; tratados como SECRET |
| previousHash, entryHash | Cadena calculada sobre todos los campos, incluidos secretos |

`OperationSessionLog`: id y createdAt son metadatos; steps contiene los pasos anteriores. Las constantes serialVersionUID y TIMESTAMP_FORMAT no se serializan.

## Creación, copia y persistencia

- `SessionTrailState.add` → `OperationSessionLog.add` → `SessionOperationStep.capture`.
- `OperationSessionLog.copy/getSteps` y `SessionOperationStep.copyOf/relink` realizan copias y mantienen la cadena.
- `SaveSessionCoordinator.handleSaveSession` solicita nombre y contraseña y llama a `SavedSessionsCoordinator.save`.
- `SavedSessionsCoordinator.save` captura UI y crea `SavedSession`; su constructor y getOperationLog copian el rastro.
- `SavedSessionCodec.prepareForStorage` crea otro SavedSession sin rastro. Con contraseña incluye el rastro íntegro en ProtectedPayload cifrado; sin contraseña lo descarta.
- `SavedSessionsManager.addSession` vuelve a preparar las sesiones sin protectedFields; `saveSessions/loadSessions` usan serialize/deserialize de Gson. La redacción deberá ser idempotente.
- `SavedSessionCodec.restore` crea la sesión descifrada; sin protectedFields devuelve la sesión original.
- Tests existentes: SavedSessionCodecTest, SavedSessionsManagerTest, OperationSessionLogTest, SessionTrailStateTest, SessionTrailViewFormatterTest y SessionTrailUITest.

## Carga actual y compatibilidad

Sin protectedFields, la carga utiliza directamente SavedSession. Con operationLog null, SavedSessionsCoordinator crea un OperationSessionLog vacío y llama a trailState.replace; la vista previa muestra sessionTrail.empty. Gson admite operationLog ausente o null: debe mantenerse ese resultado. Versiones antiguas sin trailRedacted deberán devolver false.

`SessionTrailCoordinator.export` escribe `state.log().toText()` sin filtro (exportación de laboratorio en claro), tampoco filtra títulos ni etiquetas. Se conservarán tal cual, como pide el encargo; esto es una limitación explícita. Operación y estado tampoco tienen clasificación: se conservan según la lista blanca solicitada.
