# Encargo 22: inventario previo de la interfaz heredada

Rama de trabajo: `luna/retire-legacy-shell`, creada desde `3012adb49b73b3c4e3cc68cb4240070f39a823e7`.

## Método y evidencia

El punto de entrada empaquetado es `Launcher` → `CryptoCalculatorModern`; este carga `/fxml/main-view-modern.fxml` (`CryptoCalculatorModern.java:42`) y aplica `styles.css` (`:57`) y los temas `theme-light.css`/`theme-dark.css` (`ModernMainController.java:971-972`). `package_macos.sh` y `pom.xml` también apuntan al launcher moderno. `main-view-modern.fxml` declara `ModernMainController` y sus `ModuleHost` solo enumeran los módulos modernos. La búsqueda `rg -n 'main-view\\.fxml|CryptoCalculatorApp|MainController|TestPoly|LegacyASN1Controller|PinController|CMSController|MACController' src/main src/test pom.xml package_macos.sh` confirmó que el cargador exclusivo de `main-view.fxml` es `CryptoCalculatorApp`; el otro FXML declara `MainController`.

La búsqueda de `%...` dentro de `main-view.fxml` no devuelve claves de localización, así que ese FXML no consume claves de `messages*.properties`. Los estilos modernos compartidos por la vista moderna se conservan. Los ficheros bajo `docs/legacy-fxml/` son referencias históricas, no recursos del classpath ni consumidos desde Java/FXML; se conservan como documentación.

## Inventario

| Elemento | Quién lo usa hoy | Qué se hace y por qué |
|---|---|---|
| `CryptoCalculatorApp.java` | Nadie en el arranque empaquetado; su único consumidor es el runtime `/fxml/main-view.fxml`. | Borrar: no es launcher configurado y solo abre la vista vieja. |
| `ui/MainController.java` | `CryptoCalculatorApp` y `main-view.fxml`; además actúa como tipo puente de controladores cuyo uso moderno requiere ver las filas siguientes. | Borrar una vez eliminados los puentes; no está en el grafo de entrada moderno. |
| `resources/fxml/main-view.fxml` | Solo `CryptoCalculatorApp`; no es referenciado por `main-view-modern.fxml` ni módulos modernos. | Borrar: vista heredada sin ruta de carga activa. |
| `ui/LegacyASN1Controller.java` | Instanciado solo por el inicializador del antiguo `MainController`. El flujo ASN.1 moderno se resuelve en controladores/módulos propios. | Borrar: exclusivo del shell retirado. |
| `ui/SignatureController.java` | Instanciado solo por el antiguo `MainController`; el módulo de autenticación moderno tiene su controlador propio. | Borrar: ninguna referencia Java/FXML/test moderna. |
| `ui/MACController.java` | Instanciado solo por el antiguo `MainController`. | Borrar: ninguna referencia Java/FXML/test moderna. |
| `ui/CMSController.java` | Inicializado solo desde el antiguo `MainController`; el moderno CMS Inspector usa `CmsInspectorController`. | Borrar: ninguna referencia Java/FXML/test moderna. |
| `ui/PinController.java` | Su única instancia y llamadas de operación estaban en `MainController`; `PaymentsController` solo declaraba un campo nunca inicializado/usado. El FXML moderno implementa los flujos de pagos directamente. | Borrar: el grafo de llamadas mostró que no era compartido en ejecución. |
| Puente antiguo dentro de `ui/GenericController.java` | El constructor con áreas comunes y sus fallbacks (`inputArea`/`outputArea`) no tenía consumidores; el antiguo `main-view.fxml` era la única fuente de esas áreas. | Desacoplar: se quitaron el constructor, ramas y handlers que dependían de aquellas áreas; se conserva el controlador moderno y sus controles del FXML genérico. |
| `TestPoly.java` | No aparece en referencias de producción, empaquetado ni tests. Es un `main` auxiliar que imprime disponibilidad de nombres de proveedor y longitudes de nonce, sin aserciones. | Borrar: no aporta una prueba de comportamiento. La cobertura de ChaCha20-Poly1305/XChaCha20-Poly1305 permanece en las pruebas criptográficas y UI ya existentes. |
| `styles.css`, `theme-light.css`, `theme-dark.css`, `tokens.css`, `components.css` | La vista moderna carga `styles.css` y los dos temas; FXML modernos comparten clases de estilo. Comparación de clases entre el FXML antiguo y todos los recursos modernos identificó `.button-primary`, `.button-secondary`, `.button-danger` como selectores exclusivos del FXML antiguo. | Conservar las hojas activas/compartidas y eliminar esos tres alias muertos de `components.css`; se conservan `.action-button-primary`, `.secondary-button` y `.danger-button` usados por módulos actuales. |
| Claves de `messages.properties`, `messages_es.properties`, `messages_en.properties` | El FXML heredado no tiene referencias `%clave`; las claves Java deben confirmarse con uso global antes de retirar. | Conservar en este inventario por ahora; borrar en los tres idiomas solo las claves demostrablemente no usadas por fuentes, recursos ni tests. |
| `docs/legacy-fxml/*.fxml` | Referencias archivadas; no hay referencias runtime. | Conservar: material histórico de documentación, no interfaz cargable. |
| Tests de la interfaz antigua | No se localizaron tests dedicados a `MainController`, `CryptoCalculatorApp`, `LegacyASN1Controller`, `SignatureController`, `MACController`, `CMSController` o `PinController`; no se eliminan clases de test ni se pierde cobertura de lógica compartida. `FxmlContractTest` y `ModernMainControllerFxmlStaticTest` solo verificaban FXML moderno; `FxmlQualityGateTest` incluía el viejo FXML en su inventario. | Conservar los contratos modernos; eliminar `main-view.fxml` del listado del gate. No portar tests: el `main` de `TestPoly` solo probaba disponibilidad del proveedor imprimiendo éxitos/fallos y no aportaba aserciones. |

## Cobertura del inventario

La retirada no encontró claves `%...` de localización dentro de `main-view.fxml`; por ello no se eliminan claves de ninguno de los tres ficheros de idioma. La búsqueda global de referencias mostró que la familia LegacyASN1, Signature, MAC, CMS y Pin solo recibía llamadas desde `MainController`; los equivalentes modernos (incluido `CmsInspectorController`) se conservan. Los CSS se mantienen porque el moderno los carga o los módulos consumen las hojas compartidas. Los contratos FXML modernos siguen validando la vista moderna y módulos; se quitó el FXML antiguo de los dos inventarios de recursos de producción, `FxmlQualityGateTest` y `Ux47ReleaseQualityGateTest`.

## Resultado y comprobaciones

- Línea base (`mvn -o -q test` antes de editar): 2257 tests, 0 fallos, 1 omitido.
- Rama final: `luna/retire-legacy-shell`; commits: inventario `62849d1`, retirada `c77f8d9`, cierre y gates documentales en el commit final de esta rama.
- Suite tras la retirada: 2257 tests, 0 fallos, 1 omitido. No hubo cambio de conteo: no se encontró ningún test JUnit dedicado al shell viejo que retirar o portar. `TestPoly` era un `main` de producción sin aserciones, no un test contabilizado; la funcionalidad ChaCha20/XChaCha20 ya tiene pruebas existentes.
- Los contratos `FxmlContractTest`, `FxmlQualityGateTest`, `ModernMainControllerFxmlStaticTest` y el gate `Ux47ReleaseQualityGateTest` pasan; los inventarios de FXML solo contienen recursos modernos y no mencionan `main-view.fxml`.
- Arranque empaquetado: `mvn -o -q package -DskipTests` terminó correctamente; `java -jar target/cryptocarver-2.4.0.jar --cli sha256 abc` devolvió `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
- Arranque de interfaz: `mvn -o javafx:run` abrió el shell moderno; el log registró `ModernMainController initialized successfully!` y `Modern UI launched successfully!`. Se cerró después de unos segundos.
- Claves i18n retiradas: 0 en los tres bundles, porque el FXML eliminado no contenía referencias `%clave` y no se identificó una clave Java exclusiva de los controladores retirados. CSS aliases retirados: `.button-primary`, `.button-secondary`, `.button-danger`.

No se conservó ningún controlador solo por duda: los módulos activos tienen controladores separados y sus contratos FXML permanecen. Se conserva `ModernMainController` y su división sigue abierta según el roadmap.
