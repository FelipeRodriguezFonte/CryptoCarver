# KeysController: extracción de TR-31, intercambio RSA y TR-34

Base `main` / `51c3f22`. Rama `luna/split-keys-1`. Trabajo realizado directamente con Sol, sin subagentes y sin push.

KeysController conserva la inyección de FXML y sus contratos públicos. Los handlers delegan una operación al coordinador correspondiente. Cada coordinador recibe una vista record mediante proveedor, un proveedor de StatusReporter, el callback de estado y el callback i18n. No captura controladores de otros módulos. La vista por proveedor permite que initializeTR31 siga reemplazando los controles y que init conecte el reportero después de la construcción inicial.

Las clases Tr31Logic, RsaKeyExchangeLogic y Tr34Logic procesan entradas, validan, construyen parámetros y envelopes y formatean los informes sin JavaFX. Llaman las operaciones crypto existentes. KeyDistributionValidation sólo transporta una clave de localización y un fx:id, sin el material recibido. El coordinador presenta esa validación con la redacción y el fallback originales. La publicación, clasificación de secretos y los estados de éxito/error conservan sus contratos.

Se mantienen los 287 campos privados @FXML con los mismos nombres, tipos y orden, incluidos los 39 de estos bloques. No cambia keys.fxml, ningún otro FXML, CSS, src/main/java/com/cryptocarver/crypto, pom.xml ni .mvn. Sólo los vectores públicos del test TR31OperationsTest se trasladan a Tr31TestVectors para compartir los valores existentes con las pruebas de interfaz y lógica.

## Commits

- `494d43e`: mapa previo de bloques y contratos.
- `d08cff4`: caracterización previa, fixture de vectores y soporte de rutas/pestañas en la herramienta de instantáneas.
- `ee98e05`: coordinador y lógica TR-31. El test headless de feedback sigue haciendo las mismas aserciones, sobre su nuevo propietario.
- `c1561a7`: coordinador y lógica de intercambio RSA.
- `7f03c9d`: coordinador y lógica TR-34.
- Commit final de documentación: este informe, mapa actualizado y resultados de validación.

## Caracterización antes de mover producción

[Salida y cobertura previas](keys-split-1-characterization.md). Se usó Fxml.loader sobre main-view-modern.fxml y navegación real hasta keys.fxml, con test.mode y user.home=target/test-home. RSA y certificados se generan dentro de los tests; las claves simétricas y KBPK proceden de vectores públicos preexistentes. Cada cambio explícito de AppSettings restaura el valor anterior y el lifecycle común proporciona un almacén de ajustes aislado por clase. El fixture libera shell, Stage y Scene al terminar cada test.

```text
mvn -o -q test -Dtest=KeysSplitCharacterizationUITest,TR31OperationsTest
Maven exit 0
KeysSplitCharacterizationUITest: 23 tests, 0 failures, 0 errors, 0 skipped, 22.519 s
TR31OperationsTest: 14 tests, 0 failures, 0 errors, 0 skipped, 0.018 s
```

TR-31 cubre las cuatro versiones A/B/C/D, cabecera, exportación, parseo, descifrado, un bloque publicado independiente y KBPK incorrecta localizada. RSA cubre export/import de los tres formatos y clave privada incorrecta en cada uno. TR-34 cubre una/dos pasadas, nonce manipulado y aviso visible. Cada bloque comprueba publicación en inspector/histórico/Shelf, captura/restauración portable y redacción de recetas de histórico; etiquetas y mensajes de validación responden al idioma.

Se preservan dos particularidades anteriores: TR-31 import publica parámetros/longitud, sin bytes recuperados en el snapshot; TR-34 con nonce incorrecto devuelve material con recepción marcada como no verificada. La configuración portable permite recuperar entradas editables, mientras la receta de histórico restringida redacta secretos y no repone resultados. La extracción no modifica esas políticas.

## Pruebas durante la extracción

- TR-31: caracterización completa + Tr31LogicTest + Ux22HeadlessTest, exit 0.
- RSA: caracterización completa + RsaKeyExchangeLogicTest, exit 0.
- TR-34: caracterización completa + Tr34LogicTest, exit 0.

Nuevas pruebas: 23 invocaciones UI y 20 invocaciones sin FXML (7 TR-31, 7 RSA, 6 TR-34), **43 en total**. La lógica cubre vectores, informes/localización, validación por campo, perfil RSA por defecto, metadata/perfil del envelope y precedencia sobre el selector, KCV, perfil de pasadas y nonce no verificado. Los casos negativos generan errores esperados y redaccionados; no son fallos de tests.

## Instantáneas y recortes

Antes (producción original) y después se ejecutó:

```sh
mvn -o -q test -Dtest=ComputedStyleSnapshotTool \
  -DstyleSnapshotTheme=theme-light.css \
  '-DstyleSnapshotRoute=TR-31 Key Blocks,RSA Key Exchange,TR-34 Key Distribution' \
  -DstyleSnapshotTabs=true \
  -DstyleSnapshotOut=target/keys-split-1/styles-before.txt
# En la versión extraída se cambia sólo el fichero de salida a styles-after.txt.
diff -u target/keys-split-1/styles-before.txt target/keys-split-1/styles-after.txt
```

**4987 registros por fichero; diff vacío (exit 0)**. Se recorren las pestañas de las tres pantallas además de los estados de árbol y diálogo ya existentes. La herramienta conserva su selección y comportamiento por defecto cuando no se activan las nuevas opciones.

```sh
mvn -o -q test -Dtest=ClippedTextAuditTool \
  -DclippedTextAuditLocale=es -DclippedTextAuditTheme=light \
  '-DclippedTextAuditRoutes=TR-31 Export|RSA Key Exchange|TR-34 Key Distribution' \
  -DclippedTextAuditOut=target/keys-split-1/clipped-text
```

Salida de target/keys-split-1/clipped-text/es-light.txt:

```text
Information dialog = 0
RSA Key Exchange / tabs 0:1 = 0
RSA Key Exchange = 0
TR-31 Export / tabs 0:1 = 0
TR-31 Export / tabs 1:1 = 0
TR-31 Export = 0
TR-34 Key Distribution / tabs 0:1 = 0
TR-34 Key Distribution / tabs 1:1 = 0
TR-34 Key Distribution = 0
TOTAL = 0
```

No se modificó la auditoría ni se añadieron exclusiones. La ejecución final de instantáneas/auditoría fue conjunta en una sola invocación de Maven; los comandos anteriores permiten repetirlas individualmente. La ejecución conjunta emitió warnings CSS al inicializar el árbol. El diff completo permanece vacío; no se modificó CSS. No se produjeron ni subieron imágenes.

## Suite completa

```text
mvn -o -q test
Maven exit 0
Tests: 2580; failures: 0; errors: 0; skipped: 1
Clases: 376
Tiempo total (real): 225.78 s (3 min 45.78 s)
Java heap space / OutOfMemoryError: 0
```

La base tenía 2537 tests; la suite final tiene 2580 (+43). El único omitido corresponde a Pkcs11SessionEncapsulationTest (prerrequisito HSM). ComputedStyleSnapshotTool y ClippedTextAuditTool se ejecutaron explícitamente antes de la suite y sus dos reportes previos se excluyen de este total. Los cuatro ficheros de tests añadidos pasaron también dentro de la suite completa.

Una sola ejecución de Maven a la vez, sin low-cpu ni cambios en heap/forks. El heap permanece en 2 GB. No se realizó prueba manual, tal como exige el encargo; la validación de interfaz anterior es automatizada.

## Tamaño y preparación de fase 2

| Fichero | Líneas |
|---|---:|
| KeysController antes | 6907 |
| KeysController después | 6061 |
| Tr31Coordinator | 343 |
| RsaKeyExchangeCoordinator | 183 |
| Tr34Coordinator | 179 |
| Tr31Logic | 213 |
| RsaKeyExchangeLogic | 165 |
| Tr34Logic | 150 |
| KeyDistributionValidation | 16 |

La fachada pierde **846 líneas (12,2 %)**. Los coordinadores conservan publicación/pintado y la lógica queda separada para pruebas rápidas. No se pretende reducir el número total de líneas a costa de mezclar responsabilidades.

[Mapa completo previo y campos](keys-split-1-map.md). Áreas pendientes en la versión extraída:

| Área | Localización actual | Campos / dependencia dominante |
|---|---|---|
| Generación asimétrica | cabecera 2583; handleGenerateRSA 2594 | pares y tarjetas de resumen, OperationExecutor, export/Shelf y módulos de certificados/firma |
| KDF / key wrap | initializeKDF 3404, hasta CMS | controles kdf* y keyWrap* declarados al principio; badges, validación y formatos |
| CMS / PKCS#7 | initializeCMS 4020, hasta cadena | campos recibidos por inicializadores, PKCS#11, DSS, revocación y executor |
| Cadena de certificados | initializeCertificateChain 4566 | chainInputArea, chainCrlInputArea, chainResultArea; dependencias de validación |
| Auxiliares / Key Lab | handleClear 4664, hasta LMK | resúmenes, Shelf, navegación, KeyMaterial, controles keyLab* |
| Variant LMK | handleThalesEncrypt 5734 | 8 campos FXML propios |
| Key Block LMK | handleKeyBlockInspect 5865 | 3 campos FXML propios |
| Atalla | handleAtallaGenerate 6018 | 15 campos FXML y atallaSyncing |

La parte inicial aún contiene generación simétrica, componentes, inspección de material, almacenes y PKCS#11. El mapa distingue declaraciones de controles de su uso: muchos campos pertenecientes a bloques posteriores se declaran antes de initialize, y CMS/certificados reciben controles de módulos externos mediante contratos públicos. Antes de fase 2 hay que cerrar ese inventario y conservar la política de captura/restauración de pantalla.
