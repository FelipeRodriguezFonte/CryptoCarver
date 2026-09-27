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
| `ui/SignatureController.java` | Instanciado solo por el antiguo `MainController`; el módulo de autenticación moderno tiene su controlador propio. | Borrar si el repaso de referencias final sigue confirmando uso exclusivo. |
| `ui/MACController.java` | Instanciado solo por el antiguo `MainController`. | Borrar si el repaso de referencias final sigue confirmando uso exclusivo. |
| `ui/CMSController.java` | Inicializado solo desde el antiguo `MainController` según el grafo Java/FXML inspeccionado. | Borrar si el repaso de referencias final sigue confirmando uso exclusivo. |
| `ui/PinController.java` | Inyectado en `PaymentsController`, que también se usa desde `main-view-modern.fxml`; el inicializador antiguo también lo usaba. | Conservar y desacoplar: es lógica de PIN compartida. Sustituir el parámetro/campo de `MainController` por dependencias concretas si se encuentra consumo de ese tipo. |
| `TestPoly.java` | No aparece en referencias de producción, empaquetado ni tests. Es un `main` auxiliar dentro de `src/main`. | Borrar de producción; convertir en test solo si contiene una aserción de comportamiento útil (pendiente de inspeccionar). |
| `styles.css`, `theme-light.css`, `theme-dark.css`, `tokens.css`, `components.css` | La vista moderna carga `styles.css` y los dos temas; FXML modernos comparten clases de estilo. | Conservar: recursos activos y compartidos. |
| Claves de `messages.properties`, `messages_es.properties`, `messages_en.properties` | El FXML heredado no tiene referencias `%clave`; las claves Java deben confirmarse con uso global antes de retirar. | Conservar en este inventario por ahora; borrar en los tres idiomas solo las claves demostrablemente no usadas por fuentes, recursos ni tests. |
| `docs/legacy-fxml/*.fxml` | Referencias archivadas; no hay referencias runtime. | Conservar: material histórico de documentación, no interfaz cargable. |
| Tests específicos de la interfaz antigua | No se localizó una clase `MainControllerTest` ni tests dedicados a `CryptoCalculatorApp`/`LegacyASN1Controller`; `FxmlContractTest`, `FxmlQualityGateTest` y `ModernMainControllerFxmlStaticTest` contienen checks que mencionan el nombre del FXML viejo. | Mantener y actualizar esos contratos para que solo verifiquen el FXML moderno. Revisar las demás clases de tests por dependencia o fixtures viejos antes de borrarlas; portar cobertura de lógica compartida si aparece. |

## Cobertura del inventario

La lista anterior distingue piezas exclusivas de las compartidas; la retirada debe borrar solo lo que el grafo confirme muerto y desacoplar los consumidores modernos de `MainController`. Antes de eliminar clases se comprobarán todas sus referencias, incluyendo FXML, tests y reflexión. Este documento es el inventario previo; las decisiones finales, referencias, lista exacta de tests retirados/portados y conteos de ejecución se completarán en el informe final del encargo.
