# UX-19 — Handoff de QA funcional, visual y accesibilidad

## Módulos y rutas auditadas

- ASN.1: `Decode ASN.1`, `Encode ASN.1`, alias `ASN.1`.
- XML Security/XAdES: `Sign XML`, `Verify XML`, `Inspect Signed XML`, `RFC 3161 Timestamp`.
- WSS: `Sign SOAP`, `Verify SOAP`, `Add UsernameToken`, `Verify UsernameToken`, `Encrypt SOAP Body`, `Decrypt SOAP Body`.
- JOSE: `JWT (Signed)`, `JWE (Encrypted)`, `JWK (Keys)`, `JWA (Algorithms)`, `Token Inspector`.
- CMS: `CMS Operations` y `CMS Inspector`; PAdES: `PAdES PDF Signatures`; ASiC-S: `ASiC-S Containers`.
- OpenPGP: `OpenPGP (GPG Compatible)`.
- Payments: `Clear PIN Blocks`, `Encrypted PIN Blocks`, `PIN Generation`, `CVV Operations`, `DUKPT TDES / AES`.
- EMV: `EMV Operations`, `EMV TLV Inspector`, ARQC/ARPC y Track 2.
- Process Designer: `Process Designer`, presets, ejecución, dry-run, cancelación y limpieza del canvas.

## Fallos detectados y correcciones

1. La expansión de paneles en XML, WSS y Certificates comparaba sólo el título canónico inglés. Después de cambiar a ES la navegación podía mostrar el contenedor, pero no expandir el panel solicitado. Se añadió `ModulePaneMatcher`, que compara nombre canónico, texto decorado y título vivo del catálogo ES/EN.
2. Faltaban aliases de navegación para las entradas visibles `ASN.1`, `WSS Security` y `Payments`. Se añadieron rutas tipadas al `UiNavigationRegistry`.
3. El botón Clear de ASN.1 limpiaba Decode, pero dejaba datos/resultados de Encode. Ahora limpia ambos recorridos y conserva el contrato técnico de formatos.
4. CMS Inspector, PAdES, ASiC, JOSE, XML, WSS, Payments, EMV y Process Designer disponen de una acción Reset/Clear visible con handler FXML verificable. Los resets sólo limpian entradas/resultados del módulo; no tocan historial, Clipboard Shelf ni perfiles compartidos.
5. EMV DOL/TLV y verificación ARQC actualizan también `OperationResult`, Inspector/estado e histórico, igual que las operaciones de generación existentes.
6. Se reforzó la accesibilidad del flujo de reset conservando el foco cuando el control sigue visible y gestionado. El texto nuevo `Reset` participa en `ModuleTextCatalog` e `I18nService` ES/EN.
7. Se añadieron regresiones estáticas para handlers FXML y regresiones headless por familias; también una suite JavaFX opt-in para cargar ASN.1, XML/WSS, JOSE, Payments y Process Designer y comprobar reset, contenido real, cambio ES/EN y foco.

No se modificaron algoritmos, bytes, hashes, PEM, claves, PIN, KSN, tags EMV, TR-31, XML/SOAP, JSON/JWT ni artefactos criptográficos.

## Pruebas ejecutadas

- `mvn -q test` — correcto.
- `mvn -q -Dtest=Ux19SpecializedHeadlessTest,FxmlQualityGateTest,UiNavigationRegistryTest test` — correcto.
- `xmllint --noout` sobre los FXML modificados — correcto.
- `git -c core.fsmonitor=false diff --check` — correcto.
- `mvn -q -DrunUiTests=true test` — no ejecutable en este entorno.

## Limitación JavaFX

La ejecución UI opt-in falla antes de cargar los FXML. JavaFX 21.0.5 no puede inicializar `QuantumRenderer`: falla ES2 y software porque no hay display/pipeline utilizable y el runtime no puede copiar `libprism_sw.dylib` a `/Users/feliperodriguez/.openjfx/cache/21.0.5+1/aarch64/.lock` (`Operation not permitted`). El error final es `RuntimeException: No toolkit found`. Debe repetirse en macOS con display/pipeline gráfico y caché JavaFX escribible; las pruebas `@Tag("ui")` permanecen activas y opt-in.

## Deuda pendiente priorizada

1. **Alta:** ejecutar la matriz visual completa en una sesión con JavaFX operativo, incluyendo screenshots ES/EN, orden de foco y navegación desde History/Clipboard Shelf.
2. **Alta:** completar la auditoría visual de CMS Sign/Verify/Encrypt/Decrypt y TR-31 en una sesión gráfica; en esta entrega se verificaron sus rutas/contratos FXML, pero no se pudo interactuar con el escenario completo.
3. **Media:** extraer los resets de módulos a una política común de limpieza de estado para evitar que cada controlador mantenga listas de excepciones.
4. **Media:** reemplazar progresivamente textos históricos fijos de diálogos y errores XML/WSS/Payments por claves de catálogo; los textos nuevos/corregidos de UX-19 sí están catalogados.

## Módulos opcionales no revisados en profundidad

- Compare Results.
- History.
- Clipboard Shelf.
- Batch Runner.
- TR-31: sólo se comprobó la ruta del rail/registry dentro de Keys; no se realizó la auditoría funcional completa de su panel.

No se hizo commit. Se conservaron los cambios sin commit de UX-18 y los cambios ajenos existentes en el worktree.
