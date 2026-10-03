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
