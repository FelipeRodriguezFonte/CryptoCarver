# Encargo 77: alias de historial JWKS

La restauración admite `jwksArea` como alias de lectura de `jwksSecretArea`. La captura escribe el identificador actual y, si una receta contiene ambos, gana el actual, incluso cuando su valor es vacío o nulo. La normalización ocurre antes del filtro de secretos del historial para aplicar al alias la misma política del campo actual.

## Alcance y reproducción

Base `a4287977`; rama `codex/jose-history-alias`; worktree `/Users/feliperodriguezfonte/dev/CryptoCarver-jose-6`, creado desde el commit solicitado. Todo Maven se ejecutó ahí, con una sola ejecución simultánea; no se compiló el repositorio principal.

El primer commit añade `JoseHistoryAliasUITest.oldQualifiedJwksRecipeRestoresContent`, sin cambios de producción. Antes del arreglo se ejecutó:

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=JoseHistoryAliasUITest
```

Resultado rojo: **1 informe / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1**. La receta `JOSEController.jwksArea` no restauró el JSON inventado: `legacy JWKS recipe must restore the current input`, expected PRIVATE_JWKS, actual cadena vacía. La evidencia y la incidencia del comprobador auxiliar de XML están en [jose-history-alias-77-reproduction.md](jose-history-alias-77-reproduction.md); el log completo permanece local en `target/jose77-reproduction.log`.

## Arreglo y privacidad

El cambio de producción se limita a `UiStateSnapshot`: registro del alias y resolución durante la restauración, tanto de recetas como de sesiones. Admite claves simples y cualificadas, no modifica el mapa original y resuelve la presencia del identificador nuevo antes de leer el alias. La captura, los tokens de clasificación y el resto de servicios no cambian.

El nuevo test verifica los cuatro cruces de claves simples/cualificadas, valores actuales normales/vacíos/nulos y los modos de captura. Bajo MASKED y REDACTED verifica que el alias queda en el control real `jwksSecretArea`, con clasificación SECRET, y que la receta capturada usa `[REDACTED_SECRET]` bajo el identificador nuevo. La reapertura de historial limpia la entrada y solicita reintroducción igual que una receta actual.

La fixture es un JWK simétrico privado inventado y fijo; Nimbus confirma `isPrivate()`. Las comprobaciones abarcan historial en memoria y su JSON persistido, Shelf, copia, barra de estado y el contenido del visor expandido real. No contienen el valor privado inventado. AppSettings se aísla y restaura; Shelf se conserva y restaura si cambia; el historial del test vive en un directorio temporal y se limpia al terminar. El soporte existente también limpia su historial en memoria y cierra la UI.

Pruebas focalizadas después del alias: `JoseHistoryAliasUITest,UiStateSnapshotTest,HistoryReopenCharacterizationUITest`: **3 informes / 13 pruebas / 0 fallos, errores u omitidas / exit 0**. Después de privacidad, añadiendo `JoseJwkPrivacyCharacterizationUITest`: **4 informes / 15 pruebas / 0 fallos, errores u omitidas / exit 0**.

## Puertas

Antes de cada puerta se borró `target/surefire-reports`. Cada fila cuenta exclusivamente los XML `TEST-*.xml` de esa ejecución, sin sumar resultados focalizados o anteriores. Logs y manifiestos por suite quedan en `target/jose77-G*.log` y `target/jose77-gate-results.json`.

| Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| G1 | 448 | 2916 | 0 | 0 | 1 | 0 |
| G2 | 128 | 546 | 0 | 0 | 0 | 0 |
| G3 | 128 | 546 | 0 | 0 | 0 | 0 |

Comandos exactos:

```sh
mvn -o -q test -Plow-cpu
mvn -o -q test -Plow-cpu -DrunUiTests=true
mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test
```

Las tres puertas pasan limpias. Respecto a la referencia a428797, G1 pasa de 447/2913/1 a 448/2916/1 (informes/pruebas/omitidas) y G3 de 127/543/0 a 128/546/0: se añade un informe y tres pruebas. Los tres tests de ExpandedViewerLifecycleUITest pasan en cada puerta; no hubo reejecuciones. JoseHistoryAliasUITest ejecuta sus tres pruebas en cada puerta, sin omitidas.

## Higiene y portabilidad

Se ejecutaron los contadores del job `quality-gates` de `.github/workflows/ui-tests.yml`: **0 estilos en línea en FXML; 325 emojis de un máximo de 325**, sin añadidos. `git diff --check` pasa. No se tocaron `crypto/`, `pom.xml`, ModernMainController, StatusReporter ni OperationResult. No se generaron imágenes, archivos `.local.md`, DMG ni ejecutables.

Los XML de Surefire confirman Mac OS X y Java 25 para las puertas. El CI Linux con Java 17 no se ejecutó en esta sesión. Las nuevas aserciones usan contenido fijo, identificadores y políticas de la aplicación; no fijan mensajes de excepción de proveedores/JDK, rutas, fechas, tiempos ni orden de iteración no definido. No se introducen digests nuevos ni se alteran los existentes.

## Tests y commits

**Tests existentes modificados respecto a a428797: ninguno.** Solo se añade `JoseHistoryAliasUITest`, ampliado en los pasos 2 y 3 sin cambiar ni relajar la aserción de reproducción. No se relajaron umbrales. ExpandedViewerLifecycleUITest permanece intacto.

| Paso | Commit | Contenido |
| --- | --- | --- |
| 1 | `7a184fc` | Test rojo y evidencia de reproducción |
| 2 | `706c418` | Alias de lectura y prioridad del identificador actual |
| 3 | `f1aff58` | Privacidad bajo MASKED y REDACTED |
| 4 | Commit que contiene este informe (`docs: report JWKS history alias verification gates`) | Puertas, higiene y entrega |

La rama queda limpia tras el cuarto commit. No se ha hecho push ni merge.
