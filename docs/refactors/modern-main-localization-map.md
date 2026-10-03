# Encargo 53: localización del shell

Base: `2782ad6eb749e4950b0048482ff565c5727887ae` (main). CI JavaFX #210 completado en success antes de crear `codex/modern-main-localization`.

## Mapa anterior a la extracción

ModernMainController: 2623 líneas. `localizationView()` ocupa 159 líneas; `updateNodeFonts()` 31.

`localizationView()` construye la descripción de pintura: textos de menús, barra, inspector, errores, guía, progreso y paleta; accesibilidad, ayudas, tooltips y prompts. Lee referencias FXML, mainPane, laboratoryMenu, shortcuts y las selecciones de idioma/tema. Las referencias y las claves forman construcción pura de View (no pintan controles). El tema procede de AppSettings, por lo que no debe congelarse en el factory.

El callback final sí lee estado vivo: navegación del session trail, navigationRail y sidePanel (pueden ser nulos); currentActiveOperation para breadcrumbs/favorito; texto de statusLabel para preservar estados distintos de Ready/Listo; statusBarPresenter para estado/contexto; SecretVisibilityProfile de AppSettings; error actual de inlineErrorPresenter y rootStackPane/mainPane para repintarlo. I18nService proporciona el idioma y traducciones vigentes al pintar. El foco pertenece a la Scene actual y su restauración diferida ya reside en el coordinador.

`updateNodeFonts()` recorre TextArea/TextField, ScrollPane, TitledPane, Accordion, SplitPane y Parent, respetando ese orden. Lee el árbol de nodos actual y currentFontSize (8..24, pasos de 2). Sustituye el estilo inline por Monospaced y tamaño en px; no persiste el tamaño en AppSettings. La selección de raíces mainContentArea/inspectorPanel queda en el shell. Es un recorrido parametrizable por nodo y tamaño, sin necesidad de un controlador.

## Decisión

Hay extracción razonable: factory tipado para los bindings fijos, getters perezosos para las referencias/estado que cambian y Supplier<StatusReporter> para informar. Sin campos ni parámetros ModernMainController en el coordinador. Los callbacks de integración pertenecen al shell; el coordinador consulta sus proveedores al pintar. No se pasa a initialize(). No se toca crypto ni se renombran campos/literales.

## Caracterización previa

Se parte de ModernMainShellLocalizationCharacterizationTest (10 tests) y ShellTextResolverTest (7). Se añade una transcripción del FXML de producción EN → ES → EN, menús, barra, paneles, accesibilidad, fuentes, AppSettings en memoria y recarga desde disco. El tamaño de fuente se caracteriza como estado de sesión. Cada fixture conserva/restaura AppSettings y Shelf; no vacía el Shelf del usuario. Se revisan fallos y texto antes de fijar SHA-256.

Antes de fijar el digest: primer pase UI, 2 tests/1 fallo por desreferenciar controles ausentes del FXML en la prueba nueva. Se corrigió la fixture para transcribir `<absent>` (cuatro botones legacy de barra e inspectorHistoryTitle); no es un fallo de producción. Segundo pase: 19 tests/1 fallo, únicamente la comparación deliberada BASELINE_PENDING. Se revisaron las 90 filas, EN/ES/EN, estilos 14→16→14→8→24 y persistencia ES. No se encontraron defectos de producción en esos resultados. SHA-256 sobre UTF-8 incluyendo salto final: `a57ad3eaf8e958a7768a7ecee4bbd22f16b43fee7d4e0985a1b5895df82fdbfb`. El shortcut se normaliza a SHORTCUT para independencia de plataforma.

Observación existente: shutdown() no retira el listener i18n. Las fixtures lo retiran explícitamente para evitar shells residuales al restaurar idioma; no se modifica el ciclo de vida de producción en este encargo. ShellTextResolverTest usa un resolver puro y no modifica settings/Shelf. Las dos fixtures FXML conservan la instancia previa completa de AppSettings y comprueban el Shelf íntegro, sin mutarlo.

Caracterización fijada antes de extraer: 19 tests, 0 fallos, 0 errores, 0 omitidos (10 existentes + 7 resolver + 2 nuevos), con `mvn -o -q test -Plow-cpu -Dtest=LocalizationShellCharacterizationUITest,ModernMainShellLocalizationCharacterizationTest,ShellTextResolverTest`.

## Extracción y revisión

ModernMainController: 2623 → 2474 líneas (−149). localizationView() y updateNodeFonts() son delegados de una línea. El cableado de las 101 referencias FXML queda en localizationControls(), con tipos explícitos; createView() construye los bindings sin leer AppSettings ni pintar controles. No usa reflexión ni lookup por ids. LiveState contiene Supplier<StatusReporter>, getters perezosos para paneles, operación, label, presentadores y raíz de error, y callbacks estrechos para session trail/breadcrumbs/favorito. Ningún campo, argumento o import del coordinador es un controlador de shell/módulo. Los suppliers del shell son el límite de integración requerido, no se guarda `ModernMainController` en el coordinador.

El tema se consulta en cada pintura; el contexto de privacidad y el error actual se resuelven al finalizar. El recorrido de fuentes recibe tamaño explícito, conserva ramas/orden/estilos y no toca AppSettings. Comparación mecánica de todas las líneas de bindings antes/después: idénticas al normalizar accesores y separar la lectura viva del tema. No se cambiaron literales ni campos FXML.

Caracterización después de extraer: 19 tests pasan y SHA idéntico. Regresión adicional (sin modificar transcripción): reemplazo tardío de label/presentadores, cambio de operación dentro de refreshSessionTrail, tema DARK→LIGHT, idioma ES, preservación de estado no Ready/Listo y repintado del error actual. Se mantienen los tres primeros commits separados: mapa, caracterización y extracción. Los fallos de compilación durante desarrollo (accesor generado sobre un método, forward reference y null ambiguo en fixture) se corrigieron antes de las comprobaciones; no eran fallos de comportamiento ni requirieron cambiar el digest.

## Informe final

JDK Temurin 21.0.8. Dos ejecuciones independientes, retirando los informes de Surefire entre ellas:

| Suite | Comando | Tests | Clases | Fallos | Errores | Omitidos | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| UI | `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 462 | 91 | 0 | 0 | 0 | 0 |
| Completa | `mvn -o -q test -Plow-cpu` | 2805 | 405 | 0 | 0 | 1 | 0 |

Única omisión preexistente: `Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain`. Los 10 tests de ModernMainShellLocalizationCharacterizationTest, los 7 de ShellTextResolverTest y los 3 de LocalizationShellCharacterizationUITest pasan en la suite completa. Informes independientes locales: target/localization-ui-reports y target/surefire-reports.

SHA-256 transcripción antes de extraer, después de extraer, tras UI y tras suite completa: **idéntico**, `a57ad3eaf8e958a7768a7ecee4bbd22f16b43fee7d4e0985a1b5895df82fdbfb`. Archivo generado: target/localization-shell-transcript.txt, 90 filas UTF-8 con salto final.

Digests finales de fuente:

- ModernMainController.java: `c5361d0225253b6d4182d35b0142a8ad0ac93e418c6f7b3bb7e4af3bbb497c06`.
- ShellLocalizationCoordinator.java: `262788b493566167c6c05d5f03003f715677572ab59d891720595891b5d188e9`.

ModernMainController: **2623 → 2474** líneas; ShellLocalizationCoordinator: **273 → 599**. Se conserva comportamiento y transcripción. Sin cambios de producción después de los gates. Diff completo contra 2782ad6 comprobado sin errores de whitespace; archivos limitados a los dos componentes UI, dos fixtures de pruebas y este mapa. Sin modificaciones de crypto, imágenes, archivos .local.md ni secretos reales. Cuatro commits: mapa, caracterización, extracción y este informe. Rama final `codex/modern-main-localization`, sin cambios pendientes.
