# Handoff UX-16 — accesibilidad, diálogos legacy e i18n visual

## Alcance terminado

- Shell moderna: nombres accesibles y ayuda para formato de payload/salida, paleta, favoritos, acciones del resultado, cierre del Inspector y cierre del banner inline.
- Rail y SidePanel: nombres accesibles traducibles para navegación, búsqueda y contraer panel.
- `ModuleI18n`: refresco de `accessibleText`, `accessibleHelp` y tooltips; el control enfocado se restaura después de cambiar ES/EN.
- Banner de validación: la envoltura de errores conocidos se localiza al idioma activo; `detail`, `cause`, algoritmos y datos técnicos no se transforman.
- Diálogos compartidos: histórico, sesión, configuración de pantalla y plantillas personales. Títulos, cabeceras y filtros se localizan; rutas, nombres de archivo, extensiones y mensajes técnicos se conservan literalmente.
- CSS: anillo de foco visible, borde de error para ComboBox/campos y borde adicional en estados de resultado para no depender sólo del color.
- Se añadió una prueba contractual no gráfica y una prueba UI opt-in (`-DrunUiTests=true`).
- No se modificaron algoritmos, operaciones, formatos, recetas, historial, secretos, permisos ni clases de `crypto/`.

## Ficheros modificados por UX-16

- `src/main/java/com/cryptocarver/ui/ModernMainController.java`
- `src/main/java/com/cryptocarver/ui/ModuleI18n.java`
- `src/main/java/com/cryptocarver/ui/NavigationRail.java`
- `src/main/java/com/cryptocarver/ui/SafeTemplateUIHelper.java`
- `src/main/java/com/cryptocarver/ui/SidePanel.java`
- `src/main/java/com/cryptocarver/ui/LocalizedDialogSupport.java`
- `src/main/resources/fxml/main-view-modern.fxml`
- `src/main/resources/css/styles.css`
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_es.properties`
- `src/test/java/com/cryptocarver/ui/Ux16AccessibilityContractTest.java`
- `src/test/java/com/cryptocarver/ui/Ux16AccessibilityLiveUITest.java`

Los cambios preexistentes del worktree (incluidos `.DS_Store`, `WssEncryptionOperationsTest.java`, `HistoryItem.java`, `SecretVisibility.java`, `OperationFormatState.java` y `HANDOFF_UX_PROPUESTA.md`) se conservaron sin mezclarlos en UX-16.

## Decisiones de accesibilidad e internacionalización

- Se reutilizan `I18nService`, `LanguagePreference`, `ModuleI18n` y `ModuleTextCatalog`; no se creó un catálogo alternativo.
- Los accesibles se actualizan en el mismo listener que los textos visibles. El foco se captura antes del refresco y se restaura con `Platform.runLater` sólo si el nodo sigue visible, habilitado y enfocable.
- El orden de los controles de shell sigue el FXML: menú/toolbar → formato → navegación → entrada del módulo → configuración → ejecución → resultado → exportación.
- Los estados siguen mostrando texto y/o iconografía (`SUCCESS`, warning, bloqueado, incompleto) y además tienen borde contrastado; error inline tiene título, remedio y acciones explícitas.
- La envoltura de un error se localiza; `Throwable`, clases de excepción, paths, algoritmos, estándares, OID, claims, tags EMV, KSN, PIN blocks, certificados, JWT/JWS/JWE, XML/SOAP, PEM, hashes, HEX/Base64 e IDs permanecen caller-owned.
- Los filtros mantienen exactamente los patrones técnicos: `*.json`, `*.ccconfig`, etc.

## Verificación automatizada

Resultados literales:

```text
mvn -q -DskipTests compile
Process exited with code 0
0

mvn -q -Dtest=Ux16AccessibilityContractTest,UserFacingErrorMapperTest,ModuleTextCatalogTest,ModernMainControllerFxmlStaticTest,FxmlContractTest test
Process exited with code 0
0

xmllint --noout src/main/resources/fxml/main-view-modern.fxml
Process exited with code 0
0

git diff --check
Process exited with code 0
0
```

La ejecución explícita de `mvn -q -DrunUiTests=true -Dtest=Ux16AccessibilityLiveUITest test` no pudo completarse en este entorno aislado: JavaFX informó `Screen.getMainScreen()` con lista de pantallas vacía y `Error initializing QuantumRenderer: no suitable pipeline found`. El test no se ejecuta en la suite por defecto gracias a `@EnabledIfSystemProperty(named = "runUiTests", matches = "true")`.

## Checklist manual macOS/Windows

Ejecutar con una sesión gráfica real y marcar cada casilla.

- [ ] Arranque con preferencia `Sistema/System`; verificar que el idioma sigue la configuración del sistema.
- [ ] Arranque forzado en `Español`; comprobar menú, toolbar, SidePanel y un módulo.
- [ ] Arranque forzado en `English`; comprobar menú, toolbar, SidePanel y un módulo.
- [ ] Navegar sólo con teclado desde el menú hasta una operación segura (por ejemplo, hash público) y ejecutar la acción.
- [ ] Confirmar el orden de tabulación: contexto → entrada → configuración → ejecutar/verificar → resultado → exportación.
- [ ] Con un módulo abierto y un campo enfocado, cambiar ES ↔ EN; verificar que el foco y el contenido técnico no cambian.
- [ ] Expandir y cerrar un acordeón con teclado; verificar que el foco queda en el panel/acción esperada.
- [ ] Abrir/cerrar la paleta de comandos con teclado; verificar foco en búsqueda, lista y retorno al control anterior.
- [ ] Provocar un error inline; comprobar título, remedio, `Ir al campo`, copia técnica y cierre con foco conservado.
- [ ] Provocar una validación modal compartida; comprobar envoltura traducida y detalle técnico literal.
- [ ] Revisar Inspector: navegación, cierre, exportación JSON, estado vacío y foco.
- [ ] Revisar Histórico: filtros, tabla, acciones de exportación y estado vacío.
- [ ] Revisar Clipboard Shelf: acciones de comparar/copiar, política de secretos y cambio de idioma.
- [ ] Revisar Process Designer: acordeones, filtros, cargar/guardar y cancelación.
- [ ] Revisar Payments: campos, tablas, acciones de ejecutar/verificar y exportación.
- [ ] Abrir FileChooser en ES/EN; verificar título/filtro localizado, ruta y extensiones sin mutación.
- [ ] Verificar recortes en toolbar, MenuBar, SidePanel, labels, botones y tablas en ambos idiomas.
- [ ] Verificar contraste de error, warning, éxito, deshabilitado, foco y selección; el estado debe entenderse sin color.
- [ ] macOS: revisión básica con VoiceOver o Accessibility Inspector.
- [ ] Windows: revisión básica con Narrator o Accessibility Insights/inspector equivalente.

## Deuda restante y riesgos

- Quedan diálogos específicos de módulos legacy fuera de las rutas compartidas auditadas; requieren una pasada por módulo para migrar sus títulos/filters sin tocar payloads técnicos.
- La validación visual real, VoiceOver/Narrator y pruebas de foco con ventanas abiertas siguen pendientes porque este entorno no expone pantalla.
- Algunos textos dinámicos de módulos legacy siguen siendo responsabilidad de sus controladores; `ModuleI18n` evita mutar valores runtime, por lo que deben añadirse al catálogo sólo cuando se confirme que son interfaz y no datos técnicos.
- El CSS existente contiene reglas históricas duplicadas; UX-16 añade overrides al final para no alterar estilos criptográficos ni introducir una refactorización visual amplia.
