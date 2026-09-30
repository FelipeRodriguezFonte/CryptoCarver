# Encargo 42: AAD en sesiones cifradas

Implementación directa con Sol sobre 08ec1be, en luna/saved-session-aad. Sin push ni prueba manual.

## Commits y orden

1. ae09c0b: inventario de usos, mutaciones y propuesta de AAD.
2. 4c468a4: caracterización previa y fixture antiguo generado por producción sin AAD.
3. f67381c: clase pura SavedSessionAad y seis pruebas unitarias.
4. 776eb98: cifrado nuevo con aadVersion 1 y protectedTrail.
5. ef9035f: restauración autenticada, compatibilidad y pruebas de persistencia/vista previa.
6. Documentación final: este informe y evidencia de la suite.

La caracterización 4c468a4 precede a todos los cambios de producción (primero f67381c, cifrado 776eb98 y restauración ef9035f). No se tocó producción antes de ejecutar y confirmar la caracterización.

## Evidencia previa

Comando: `mvn -o -q -Dtest=SavedSessionAadCharacterizationTest test`.

```text
Test set: com.cryptocarver.model.SavedSessionAadCharacterizationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.848 s
```

El test transplantedProtectedFieldsDecryptWithoutBinding_currentBehavior pasó: el bloque del donante descifraba en otro id y operation. Véase encargo-42-characterization.md para los cuatro comportamientos y la corrección justificada de la comparación del fixture con UUID aleatorios.

En el código final ese test se llama transplantedProtectedFieldsAreRejectedWithSessionBinding y exige el rechazo. En la iteración final se corrigieron dos comparaciones textuales de JSON por comparación estructural completa: el orden de iteración de Map.of puede variar entre JVM y el codec separa campos seguros y secretos. La diferencia era exclusivamente el orden de propiedades; se conserva la comparación de todo el contenido, incluido el rastro.

## Contrato del AAD

Secuencia binaria determinista, big endian:

1. Etiqueta UTF-8 CryptoCarver/saved-session, prefijada por longitud en bytes.
2. Versión de AAD (int 1).
3. id y operation, cada uno prefijado por longitud UTF-8; null usa -1 y vacío usa 0.
4. Número de claves cuyo valor recibido en uiState es exactamente [REDACTED_SECRET], seguido de las claves ordenadas con comparación natural, cada una prefijada por longitud. Una clave null se distingue inequívocamente y se ordena primero.
5. Boolean protectedTrail: indica si ProtectedPayload contiene un rastro, incluso vacío.

protectedTrail se almacena en ProtectedFields porque antes de descifrar no se puede inspeccionar el rastro cifrado. El indicador participa en el AAD: manipularlo invalida GCM. Las claves se reconstruyen desde el estado recibido; no se acepta una lista de claves del donante que permita alterar los marcadores externos.

Se ligan identidad y estructura inmutables con ciphertext vigente. Nombre, timestamp, version de sesión y valores seguros quedan fuera. El indicador de redacción de un rastro tampoco identifica el ciphertext y queda fuera. El AAD no autentica todo el documento ni elimina los riesgos de textos libres introducidos por el usuario.

No se modificaron AES-GCM, KDF, PasswordFieldCipher, iteraciones (600000), sal (16 bytes) ni nonce (12 bytes). Los errores de restore usan IllegalArgumentException("Incorrect password or modified saved session"), sin causas que pudieran revelar valores o claves del parser; no se distingue manipulación de contraseña incorrecta. Se borran los arrays de contraseña y plaintext como antes.

## Compatibilidad

| Documento | Código nuevo | Resultado |
| --- | --- | --- |
| Antiguo cifrado, aadVersion ausente o 0, ciphertext sin AAD | Descifra sin AAD | Recupera secretos y rastro; mantiene la limitación antigua de ausencia de vinculación |
| Nuevo cifrado, aadVersion 1 | Reconstruye y verifica AAD | Recupera secretos y rastro con identidad/estructura intactas |
| Nuevo con aadVersion 0 o eliminado | Intenta descifrar sin AAD | GCM rechaza; no hay degradación válida |
| aadVersion desconocido o negativo | Rechaza antes de descifrar | Error genérico sin datos |
| Nuevo con id, operation, marcadores o protectedTrail modificados | AAD diferente | Mismo error que contraseña incorrecta |
| Antiguo cargado | Solo deserializa / restaura copia | No reescribe ni migra el ciphertext |
| Antiguo restaurado y vuelto a guardar con contraseña | prepareForStorage cifra con versión 1 | Actualiza a AAD explícitamente al guardar |
| Sesión sin contraseña | Camino de redacción existente | Sin cambios |

El antiguo constructor ProtectedFields sigue disponible. Gson deja los nuevos campos ausentes en 0 y false. La vista previa trata ambas versiones como cifradas y no muestra los valores; el gestor conserva el sobre al añadir/persistir y al limpiar sesiones antiguas en claro. Código antiguo no puede descifrar las nuevas sesiones con AAD; esta compatibilidad inversa no forma parte del objetivo.

## Inventario de operaciones

El inventario completo y los usos con líneas están en encargo-42-inventory.md. No existen flujos dedicados de renombrar, duplicar, importar, exportar o fusionar sesiones guardadas. serialize/deserialize y la migración del fichero conservan los datos. El gestor añade sesiones cifradas sin modificar su sobre ni su identidad. restore devuelve una sesión separada y conserva id y operation. La eliminación de secretos solo transforma sesiones no versionadas en claro; no cambia las cifradas versionadas. timestamp y version tienen setters y se copian durante restore, por lo que se excluyen. Guardar desde el estado cargado crea una sesión y cifra de nuevo. Un futuro duplicado con id distinto deberá recifrar.

## Pruebas

29 tests nuevos: caracterización/contratos (4), codificación AAD (6), almacenamiento y constructor antiguo (2), restauración y compatibilidad (16), vista previa real antigua/nueva (1).

Cobertura: ida y vuelta completa, contraseña incorrecta, trasplante, modificación individual de id/operation, quitar/añadir/renombrar clave y sustituir marcador, degradación a 0 y eliminación de aadVersion, versión desconocida/negativa, indicador de rastro, id null y distinción de vacío, secretos sin rastro y rastro sin secretos, metadatos editables y orden, fixture antiguo, carga sin escritura, actualización al guardar y recarga desde disco. Los fallos comprueban el mensaje, ausencia de contraseña/clave/valor y causa, borrado del array y ausencia de mutación del origen. No se modifica AppSettings.

Pruebas enfocadas: 51 tests sin fallos ni errores; tras añadir la cobertura de vista previa, 8 tests afectados sin fallos ni errores. Suite completa ejecutada una sola vez al final con `mvn -o -q test` (sin perfil low-cpu):

```text
Exit code: 0
Surefire suites: 368
Tests run: 2530, Failures: 0, Errors: 0, Skipped: 1
Wall time: 216.116 seconds (3:36)
```

Totales agregados desde los XML de Surefire tras finalizar Maven; aumento de 29 tests respecto a los 2501 de la base. No hubo Java heap space. Las advertencias de JVM/localización no produjeron fallos.

## Alcance y seguimiento

`git diff 08ec1be --stat` confirma cambios solo en modelos, tests, un fixture JSON y docs/refactors. Sin cambios en crypto/, CSS, FXML, pom.xml ni .mvn/. Sin imágenes. No se hizo prueba manual y no se realizó push.

ScreenConfigurationCodec sigue usando AAD fijo de formato y versión. Revisar en otro encargo si hay identidad o estructura externa estable que deba ligarse. Su configuración completa ya está dentro del ciphertext y se importa de forma portátil; añadir una identidad del destinatario requeriría definir primero el contrato de portabilidad y compatibilidad. No se cambió aquí.
