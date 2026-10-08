# Encargo 78 — initialize y showSection

Completado en dos commits, con seis puertas limpias. JOSEController pasa de 1055 a 810 líneas (245 menos).

Worktree `/Users/feliperodriguezfonte/dev/CryptoCarver-jose-7`, rama `codex/jose-controller-3`, base `d685778`. Creado con el comando solicitado. Trabajo y todas las Maven exclusivamente aquí, sin ejecuciones simultáneas.

| Fase | JOSEController antes | Después | Nuevo coordinador |
|---|---:|---:|---|
| 1 initialize | 1055 | 859 | JoseInitializationCoordinator: 312 líneas |
| 2 showSection | 859 | 810 | JoseSectionCoordinator: 79 líneas |

La fase 1 mantiene initialize como secuencia de llamadas nombradas; la tabla JWA continúa en su punto original. View vivo, Supplier de reporter y referencias a los coordinadores JWK/JWT existentes; binding de ModuleI18n y listener de locale fuertemente retenidos por el nuevo coordinador. Mapas: [fase 5](jose-controller-5-map.md) y [fase 6](jose-controller-6-map.md). No se renombra ningún campo FXML ni fx:id.

## Caracterización

Test nuevo de arranque: inventario de los 255 controles FXML más paneles nombrados y columnas JWA (268 entradas iniciales); selección/opciones, texto/prompt, visible/managed/disabled, checkboxes y tabla. Estímulos separados con FXML fresco para los listeners de arranque y los instalados por coordinadores existentes, cuatro plantillas y locale EN→ES. Filtros de captura detached ejercidos bajo MASKED/REDACTED. Digest portable: `61e52224fbedb92086d81b19dbd2f3b2aad54df991d7e9dcb8c637e5b011b6df`, verde antes y después de extraer.

Test nuevo de secciones: rutas JOSE ordenadas, prefijos/sufijos, desconocido, minúsculas, vacío y null; FXML nuevo por estímulo, todos los paneles inicialmente expuestos para comprobar su reset. Fija estados finales y orden de eventos managed/visible de los seis paneles. Digest `781e560222264aed10bc3396167b9bafa86991fe0e679204608024052963c81e`, verde antes y después de extraer; [captura provisional de fase 6](jose-controller-6-characterization-failures.md).

Se registran las capturas provisionales y sus ampliaciones de inventario en [fallos de fase 5](jose-controller-5-characterization-failures.md). No se observó un defecto previo. Solo se normalizan los tiempos y UUID generados por las plantillas; orden de controles explícito y listas del código intactas, sin texto de excepciones de proveedores. Los fixtures reutilizan JoseCharacterizationSupport/UiTestLifecycleExtension: AppSettings restaurado, Shelf exigido inalterado, historial aislado en memoria y liberado; no crean entradas de historial real ni ficheros de entrada.

## Puertas

Antes de cada puerta se elimina target/surefire-reports. Los recuentos proceden exclusivamente de TEST-*.xml de esa ejecución tras terminar Maven. G1=`mvn -o -q test -Plow-cpu`; G2=`mvn -o -q test -Plow-cpu -DrunUiTests=true`; G3=`mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`.

| Fase | Puerta | XML | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | G1 | 455 | 2930 | 0 | 0 | 1 | 0 |
| 1 | G2 | 133 | 553 | 0 | 0 | 0 | 0 |
| 1 | G3 | 133 | 553 | 0 | 0 | 0 | 0 |
| 2 | G1 | 456 | 2931 | 0 | 0 | 1 | 0 |
| 2 | G2 | 134 | 554 | 0 | 0 | 0 | 0 |
| 2 | G3 | 134 | 554 | 0 | 0 | 0 | 0 |

Los seis exit son 0. La excepción GC no fue necesaria: ExpandedViewerLifecycleUITest pasa sin modificaciones ni exclusiones. G1 conserva la omitida de referencia; los incrementos frente a d685778 son exactamente un test/XML en fase 1 y dos en fase 2. Recuentos y comandos completos: [JSON de validación](jose-controller-78-validation.json).

Entorno local confirmado por XML de Surefire: macOS aarch64, Java 25, Maven 3.9.11. La transcripción usa APIs disponibles en Java 17 y normalización portable; no se afirma una ejecución local Linux/Java 17.

## Tests existentes, límites e higiene

Ningún test existente modificado. SpecializedFeedbackHeadlessTest conserva cada propietario; Ux24HeadlessTest/Ux25HeadlessTest y ModernMainControllerFxmlStaticTest conservan traducciones/fx:id/contratos FXML. Ejecutados en un lote focal junto al nuevo test antes de extraer, y de nuevo antes de las puertas. En fase 2 se ejecutaron los cuatro contratos fuente/FXML solos: 4 XML / 39 pruebas / exit 0, antes de las puertas. Las caracterizaciones previas de capacidades y OKP pasan intactas tras extraer. El mapa enumera clave a clave las que se mueven y las que permanecen.

Sin cambios en crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. Los campos FXML se compararon con d685778 y son idénticos en nombre, tipo y orden. Una comparación mecánica del cuerpo de showSection, deshaciendo únicamente los accesos de View, devuelve igualdad exacta con el original.

Comandos exactos del job quality-gates de .github/workflows/ui-tests.yml: estilos inline FXML=0; emojis=325/325. Sin imágenes, .local.md, DMG ni ejecutables añadidos. Hallazgos no corregidos: no se detectó un defecto funcional previo en las nuevas caracterizaciones. Se preserva la reinstalación de listeners/filtros al cambiar idioma, descrita en el mapa.

## Commits

1. `b193a67` — `refactor(jose): extract ordered initialization wiring`; fase 1, tres puertas limpias.
2. `refactor(jose): extract section selection coordinator` — fase 2, este commit; tres puertas limpias. El hash se obtiene con `git rev-parse HEAD` en la entrega de esta rama.

Estado de entrega: rama limpia después de este commit; sin merge, push ni compilación en el repositorio principal.
