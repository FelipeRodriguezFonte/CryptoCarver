# Segunda continuación — B aplicado; G1 previa a A falla

Base de este paso: `7a3bcaa`. Cambio B: `26a1a17`.

## Corrección de captura por contenido

UiStateSnapshot cambia exclusivamente la condición de captura HISTORY_RECIPE con redacción activa: además del nombre sensible, un String detectado como material privado se guarda como `[REDACTED_SECRET]`. No cambia restauración, captura portátil, campos, FXML ni otros métodos del fichero. FULL_LAB conserva los textos. No se limpia ningún editor ni historial para hacer pasar reproducciones.

PrivateKeyMaterialDetector mantiene las reglas existentes y añade una envoltura para devolver false ante RuntimeException o StackOverflowError durante detección de una entrada inválida. Se comprueban texto arbitrario, JSON inválido, base64 inválido, entradas largas (hasta dos millones de caracteres) y JSON profundamente anidado. Los casos PEM, JWK privado d, JWK simétrico k, JSON inválido con el patrón privado reconocido anteriormente, estructuras anidadas y JWT siguen detectándose; JWK público no se clasifica como privado. No se afirma poder procesar entradas mayores que los recursos físicos de la JVM.

UiStateSnapshotTest recibe un fixture nuevo con campo neutro `ContentRecipeController.notesArea` y una prueba parametrizada en los tres perfiles. PEM, JWK d y JWK k se redactan en MASKED/REDACTED; JWK público se conserva; FULL_LAB conserva todos. También se comprueba que el editor y la captura portátil siguen intactos. Ninguna aserción anterior se modifica. La restauración no se cambia.

## Puerta obligatoria inmediatamente después de B

```sh
mvn -o -q test -Plow-cpu
```

Se borra target/surefire-reports antes de esta única ejecución Maven. Resultado completo: **459 informes XML / 2957 pruebas / 8 fallos / 0 errores / 1 omitida / exit 1**. No se suman informes de ejecuciones anteriores.

| Clase relevante | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| PrivateKeyMaterialDetectorTest | 2 | 0 | 0 | 0 |
| UiStateSnapshotTest | 14 | 0 | 0 | 0 |
| WalletPrivacyCharacterizationUITest | 9 | 0 | 0 | 0 |
| WalletAdditionalPrivacyCharacterizationUITest | 12 | 8 | 0 | 0 |
| ExpandedViewerLifecycleUITest | 3 | 0 | 0 | 0 |

Los dos FULL_LAB SD-JWT originales siguen pasando. La reproducción SD-JWT completa queda verde sin modificar su archivo. Todas las clases salvo WalletAdditionalPrivacyCharacterizationUITest quedan sin fallos ni errores; no aparece ningún test de otro módulo que requiera ajustar una expectativa de conservación/restauración privada en un campo neutro.

## Ocho fallos de G1 y decisión

Son los mismos ocho casos de la auditoría ampliada que ya fallaban antes de B:

| Manejador | Entrada | Perfiles | Control |
|---|---|---|---|
| handleMdocInspect | JWK privado | MASKED, REDACTED | mdocVerifyOutputArea |
| handleMdocInspect | PEM privado | MASKED, REDACTED | mdocVerifyOutputArea |
| handleStatusListDescribe | JWK privado | MASKED, REDACTED | statusListResolveOutputArea |
| handleStatusListDescribe | PEM privado | MASKED, REDACTED | statusListResolveOutputArea |

Los JWK llegan además al resultado del shell, Shelf y visor expandido. Los PEM se detectan en el control de resultado. Son fugas autorizadas para corrección por A, pero la secuencia pedida ejecuta G1 **antes** de A. El pom existente activa UI por defecto; por tanto G1 también ejecuta las reproducciones aún pendientes de A.

La instrucción conserva como motivo de parada “una puerta que no pasa limpia (salvo la excepción de GC)”. Se aplica esa parada al resultado de esta G1, sin excluir ni relajar las reproducciones, sin adelantar A y sin invocar la excepción GC (sus tres pruebas pasan). No hay extracción que retirar. Para continuar la secuencia hace falta resolver este orden: permitir A después de esta G1 con esos ocho fallos conocidos, o autorizar que esta G1 se repita después de A. No se presupone ninguna de esas excepciones.

Se conservan las dos paradas anteriores como registro histórico; esta parada es por puerta fallida. A en mdoc/Status List y las fases 1–3 no comienzan. No se toca crypto/, pom.xml, ModernMainController, StatusReporter ni OperationResult. Único test existente ampliado: UiStateSnapshotTest por instrucción expresa B; sus aserciones anteriores permanecen intactas.

Higiene ejecutada con el bloque literal del job quality-gates: inline=0, emoji=325. git diff --check pasa. La advertencia de traducción preexistente sigue sin corregirse. Maven/Surefire usó OpenJDK 25 en macOS y release de compilación 17; no se afirma ejecución Linux/Java 17.
