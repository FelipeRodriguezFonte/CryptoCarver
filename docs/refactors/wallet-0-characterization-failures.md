# Fase 0 — fuga confirmada; extracción detenida

Base de producción: `eb3d7448`. No se ha modificado producción ni corregido la fuga.

## Reproducción

`WalletPrivacyCharacterizationUITest` carga `main-view-modern.fxml`, materializa Wallet mediante el shell, usa sus controles FXML reales, despliega el TitledPane correspondiente y mantiene un Stage con Scene. Genera dos claves EC de laboratorio. Emite un SD-JWT firmado con claims `sub=invented-wallet-79` y `cnf.jwk` que contiene accidentalmente la clave privada del holder. La firma usa otra clave, la del emisor. La clave pública del emisor permite ejecutar Verify con éxito. Inspect recibe el mismo token local. No hay resolución de URLs ni material real.

El secreto rastreado es el parámetro privado `d` del JWK del holder, no la mera presencia de nombres de campos, ni un valor interno, ni el texto que el usuario acaba de pegar en el editor de claves. La emisión y la verificación llegan a publicar resultados; se comprueba el incremento de historial y una salida no vacía.

| Manejador | Perfil | Control FXML que expone `d` | Otras superficies confirmadas |
|---|---|---|---|
| handleSdJwtVerify | MASKED | sdJwtVerifyOutputArea | Resultado del shell, historial, Shelf, visor expandido, history.json |
| handleSdJwtVerify | REDACTED | sdJwtVerifyOutputArea | Resultado del shell, historial, Shelf, visor expandido, history.json |
| handleSdJwtInspect | MASKED | sdJwtInspectOutputArea | Resultado del shell, historial, Shelf, visor expandido, history.json |
| handleSdJwtInspect | REDACTED | sdJwtInspectOutputArea | Resultado del shell, historial, Shelf, visor expandido, history.json |

El control se comprueba después de desplegar su panel y aplicar CSS/layout; está visible y conectado a la Scene. El visor expandido se abre y se lee su `contentArea`. Shelf se acciona con `handleAddCurrentOutputToShelf` y se leen sus entradas. El historial se consulta tanto en HistoryManager como en el fichero JSON aislado escrito por él. No se concluye una fuga solo porque OperationResult contenga bytes. Inspector y barra de estado no contienen `d` en esta reproducción.

## Ejecución confirmatoria

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dtest=WalletPrivacyCharacterizationUITest
```

XML de la clase: **1 informe / 9 pruebas / 4 fallos / 0 errores / 0 omitidas; exit 1**. Es una ejecución dirigida de auditoría, no G1/G2/G3. Los tres casos de la matriz ordinaria (10 operaciones por perfil) pasan: SD-JWT issue/present/verify/inspect, mdoc issue/verify/inspect y Status List issue/resolve/describe. Los dos casos FULL_LAB de JWK anidado también pasan y mantienen el informe original con `d`. Los cuatro fallos son exclusivamente las combinaciones de la tabla.

Primera ejecución exploratoria: 9 pruebas, 4 fallos, 0 errores, 0 omitidas; los cuatro fallos eran la comprobación de conexión a Scene, porque el Accordion estaba plegado. No se declaró fuga con esa ejecución. Se ajustó únicamente la preparación de la nueva prueba para desplegar el panel real y se repitió; la ejecución confirmatoria es la evidencia de la tabla. No se fija SHA-256 de una auditoría fallida ni se incluyen firmas, fechas o claves aleatorias en una transcripción portátil.

La JVM de Maven/Surefire observada es OpenJDK 25 en macOS; el proyecto compila con release 17. No se afirma haber ejecutado CI Linux/Java 17. El defecto se observa en controles y JSON, sin depender de textos de excepciones de esa JVM.

## Estado y límites

Se detienen las fases 1–3 según la instrucción de fase 0. No se extraen coordinadores ni se ejecutan sus nueve puertas. La excepción de GC no interviene. No se cambian tests existentes, umbrales, crypto, FXML ni APIs protegidas.

AppSettings y preferencias se restauran; HistoryManager usa un fichero temporal; OperationHistory conserva y restaura entradas y ruta; Shelf se restaura incluso tras fallo, mediante la fixture existente. La prueba no conserva los secretos aleatorios en mensajes de aserción. El rechazo de claves privadas en entradas que esperan claves públicas, y las superficies de sesiones/exportación de fichero distintas del historial, quedan sin certificar: no se amplía la auditoría después de confirmar el criterio de parada.

Advertencia preexistente observada: `Missing localization key: module.process.category.wallet / eidas (locale=en)`. No se corrige.
