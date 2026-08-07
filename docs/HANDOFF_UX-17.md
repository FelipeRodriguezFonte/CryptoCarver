# HANDOFF UX-17 — calidad de release y validación de interfaz

## Alcance terminado

- Se separaron las suites con JUnit 5/Surefire:
  - unidad + smoke FXML: `mvn -q test`;
  - UI real: `mvn -q -DrunUiTests=true test`;
  - integración/sockets/PAdES: `mvn -q -Pintegration-tests test`;
  - benchmarks: `mvn -q -Pbenchmark-tests test`.
- La suite por defecto excluye `ui`, `integration` y `benchmark`; el smoke FXML y las comprobaciones de rutas siguen siendo headless y obligatorias.
- El antiguo `TestTr31.java` era una clase `main` cuyo nombre coincidía con el patrón de descubrimiento de Surefire. Se renombró a `Tr31ManualSmoke` y se añadió `Tr31ReleaseSmokeTest` como smoke JUnit reproducible. No se modificó ningún algoritmo ni formato TR-31.
- `FxmlQualityGateTest` valida los 24 FXML de producción: XML seguro, `fx:controller` resoluble y todos los `onAction` presentes. Detectó tres handlers legacy ausentes; se añadieron puentes de FileChooser en `MainController` sin cambiar la operación criptográfica.
- `scripts/quality-gate.sh` y `scripts/quality-gate.bat` consolidan tests, XML, empaquetado y `git diff --check`.

## Ficheros modificados por UX-17

- `pom.xml`
- `src/main/java/com/cryptocarver/ui/MainController.java`
- `src/test/java/com/cryptocarver/LocalApiServerTest.java`
- `src/test/java/com/cryptocarver/crypto/PadesOperationsTest.java`
- `src/test/java/com/cryptocarver/crypto/TestTr31.java` (renombrado/eliminado del patrón de Surefire)
- `src/test/java/com/cryptocarver/crypto/Tr31ManualSmoke.java`
- `src/test/java/com/cryptocarver/crypto/TestAes.java` (renombrado/eliminado del patrón de Surefire)
- `src/test/java/com/cryptocarver/crypto/AesManualSmoke.java`
- `src/test/java/com/cryptocarver/crypto/TestEmvOptA.java` (renombrado/eliminado del patrón de Surefire)
- `src/test/java/com/cryptocarver/crypto/EmvOptAManualSmoke.java`
- `src/test/java/com/cryptocarver/crypto/TestEmvOptAReverse.java` (renombrado/eliminado del patrón de Surefire)
- `src/test/java/com/cryptocarver/crypto/EmvOptAReverseManualSmoke.java`
- `src/test/java/com/cryptocarver/crypto/Tr31ReleaseSmokeTest.java`
- `src/test/java/com/cryptocarver/crypto/TsaAuthDataLoaderTest.java`
- `src/test/java/com/cryptocarver/crypto/TsaDiagnosticsTest.java`
- `src/test/java/com/cryptocarver/crypto/pqc/PQCBenchmarkTest.java`
- `src/test/java/com/cryptocarver/ui/ClipboardShelfInjectionTest.java`
- `src/test/java/com/cryptocarver/ui/ClipboardShelfUX13UITest.java`
- `src/test/java/com/cryptocarver/ui/CmsInspectorControllerTest.java`
- `src/test/java/com/cryptocarver/ui/FileCipherIntegrationTest.java`
- `src/test/java/com/cryptocarver/ui/FxmlQualityGateTest.java`
- `src/test/java/com/cryptocarver/ui/KeyCertificateWorkbenchControllerUITest.java`
- `src/test/java/com/cryptocarver/ui/KeyCertificateWorkbenchUIFlowTest.java`
- `src/test/java/com/cryptocarver/ui/ProcessDesignerControllerTest.java`
- `src/test/java/com/cryptocarver/ui/SidePanelTest.java`
- `src/test/java/com/cryptocarver/ui/UiStateSnapshotTest.java`
- `src/test/java/com/cryptocarver/ui/WssSecurityControllerTest.java`
- `src/test/java/com/cryptocarver/ui/XMLSignatureControllerUITest.java`
- `src/test/java/com/cryptocarver/ui/component/MaterialFieldBadgeTest.java`
- `scripts/quality-gate.sh`
- `scripts/quality-gate.bat`

Los `.DS_Store`, `WssEncryptionOperationsTest.java`, `docs/HANDOFF_UX_PROPUESTA.md` y los modelos no relacionados que ya estaban sucios en el worktree no forman parte de UX-17.

## Decisiones de calidad

- La pantalla no se inicializa en la suite por defecto. Los tests que crean JavaFX tienen `@Tag("ui")` y `@EnabledIfSystemProperty(runUiTests=true)`; esto evita que una máquina CI sin pantalla falle antes de ejecutar unidad.
- Los tests que levantan `HttpServer` o ejecutan PAdES con PDFBox están etiquetados como `integration`; no se oculta su resultado, se ejecutan con un gate explícito en una máquina que permita sockets y runtime completo.
- El metadata de versión continúa teniendo una única fuente en `pom.xml`; `cryptocarver-build.properties`, `BuildInfo` y diagnóstico consumen esa versión filtrada. El empaquetado usa los perfiles existentes y genera el SBOM sólo con `release-artifacts`.
- El gate FXML usa un parser XML sin DTD ni entidades externas. La validación dinámica con `FXMLLoader`, navegación, foco, accesibilidad y ausencia de recortes sigue en la suite UI opt-in y en la checklist manual.
- No se añadieron dependencias, algoritmos, formatos, secretos, permisos, contratos de resultado ni transformaciones de datos técnicos.

## Pruebas ejecutadas y resultados literales

Verificaciones verdes en este checkout:

```text
mvn -q -Dtest=Tr31ReleaseSmokeTest,TR31OperationsTest test       -> exit 0
mvn -q -Dtest=FxmlQualityGateTest test                            -> exit 0
mvn -q test                                                      -> exit 0
mvn -q -Pintegration-tests test                                  -> exit 0
```

La suite headless incluye `Tr31ReleaseSmokeTest`, `UiNavigationRegistryTest`, `Ux16AccessibilityContractTest` y `FxmlQualityGateTest`. La salida conserva warnings preexistentes de claves de localización de tests (`missing.ux15b.key`, `missing.ux15c.key`, `missing.ux15d.key`) y del caso TR-31 legacy DUKPT; no son fallos de la suite.

Verificación adicional de empaquetado/XML:

```text
bash scripts/quality-gate.sh                                  -> ejecutar en máquina de release
for fxml in src/main/resources/fxml/*.fxml; do xmllint --noout "$fxml"; done
git diff --check
```

Ejecución realizada en este checkout: `bash scripts/quality-gate.sh` -> exit 0, salida final literal `[quality-gate] PASS`.

La UI real no se pudo ejecutar en este entorno restringido: JavaFX terminó con `Graphics Device initialization failed ... no suitable pipeline found`, después de no poder escribir su cache (`.openjfx/.../.lock: Operation not permitted`) y reportó `No toolkit found`. Esto es un bloqueo ambiental, no se marca como verde.

## Quality gate manual macOS/Windows

Ejecutar con una pantalla física o sesión gráfica local, y guardar fecha, OS, JDK, resolución, escala y resultado en la incidencia de release.

### Arranque, idioma y versión

- [ ] macOS: arrancar el artefacto empaquetado con `Español`; comprobar título, menú, toolbar, SidePanel, ayuda, validaciones y About/Diagnostics.
- [ ] macOS: repetir con `English`.
- [ ] macOS: repetir con `System` con el idioma del sistema en español e inglés.
- [ ] Windows 10/11: repetir las tres opciones y comprobar fuentes, escala 100/125/150% y ventana maximizada.
- [ ] Confirmar que la versión y canal mostrados por About/Diagnostics coinciden con `pom.xml` y el perfil de release.

### Teclado, rutas y foco

- [ ] Desde el menú, usar sólo teclado hasta una operación segura de cada módulo principal; ejecutar una acción de muestra no destructiva y volver al resultado.
- [ ] Verificar el orden `contexto → entrada → configuración → ejecutar/verificar → resultado → exportación`.
- [ ] Cambiar ES/EN con un módulo abierto y foco dentro de un campo; el foco queda en el mismo control y se actualizan texto visible, `accessibleText`, `accessibleHelp` y tooltip.
- [ ] Expandir/contraer acordeones, abrir/cerrar paleta, cancelar una operación y mostrar un error inline; el foco no desaparece ni salta al inicio.
- [ ] Abrir FileChooser, cancelar y volver a la operación; no se altera ruta, nombre ni extensión técnica.

### Feedback y accesibilidad

- [ ] Provocar un error inline y una validación modal en ES y EN; comprobar envoltura localizada y detalle técnico literal.
- [ ] Revisar estado error, warning, éxito, deshabilitado, foco y selección sin depender sólo del color.
- [ ] macOS: VoiceOver o Accessibility Inspector; Windows: Narrator o Accessibility Insights/inspector disponible.
- [ ] Confirmar nombre, rol, estado y ayuda de botones, menús, ComboBox, TextArea de resultado, tablas, acordeones, filtros y exportaciones.

### Módulos y operaciones seguras

- [ ] Inspector: abrir, cambiar idioma con foco, revisar resultado y exportación.
- [ ] History: filtro, selección, exportación y estado vacío.
- [ ] Clipboard Shelf: añadir una muestra no secreta, seleccionar, copiar, expandir y limpiar.
- [ ] Process Designer: crear/validar una receta educativa sin ejecutar una operación destructiva.
- [ ] Payments: usar datos de prueba no sensibles, revisar PIN block/KSN/EMV como valores literales y limpiar el resultado.
- [ ] Ciphers, Keys, Certificates, JOSE, PQC, XML/WSS, ASiC/CMS, ASN.1, PAdES, OpenPGP, Generic y EMV: abrir cada ruta publicada y comprobar que carga su handler, sin ejecutar material real.

### QA visual

- [ ] No hay truncamiento en MenuBar, toolbar, SidePanel, formularios, botones, tablas, resultados ni diálogos en ES/EN.
- [ ] Los acrónimos técnicos siguen cortos y sin traducir: AES, RSA, JWT, TR-31, EMV, XAdES, PKCS#11, PEM, HEX, Base64.
- [ ] No hay scroll horizontal inesperado ni botones de acción fuera de viewport; usar wrapping/tooltip sólo cuando el texto no pueda crecer.
- [ ] Contraste y anillo de foco son visibles en tema claro, oscuro y alto contraste.

## Release de cierre

1. Ejecutar `bash scripts/quality-gate.sh` en macOS o el equivalente `scripts\quality-gate.bat` en Windows.
2. Ejecutar `mvn -q -DrunUiTests=true test` con pantalla y revisar cualquier fallo UI.
3. Ejecutar `mvn -q -Pintegration-tests test` en un entorno con sockets permitidos.
4. Construir el artefacto final con `RELEASE_CHANNEL=stable bash scripts/build-release.sh` cuando la checklist visual esté verde.
5. Crear los paquetes nativos en su OS: `./package_macos.sh` en macOS y `package_windows.bat` en Windows; verificar el checksum desde una máquina limpia.
6. Adjuntar logs, versión, canal, JDK, OS, resolución, escala, resultado de VoiceOver/Narrator y capturas de cualquier recorte.

## Deuda y riesgos restantes

- La ejecución UI real en macOS/Windows y la revisión con lector de pantalla están pendientes de una sesión gráfica autorizada.
- Los tests de sockets y PAdES necesitan un job de integración con permisos de red local y runtime estable; no deben volver a la suite headless.
- Los warnings de claves `missing.ux15*.key` pertenecen a pruebas de regresión de i18n y deben limpiarse en una tarea separada si se desea una salida sin warnings.
- No abrir nueva funcionalidad ni nuevos algoritmos hasta que los gates UI, integración y empaquetado estén verdes en ambos sistemas operativos.
