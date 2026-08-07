# HANDOFF UX-15A — Internacionalización progresiva de módulos prioritarios

## Alcance

Se extendió la infraestructura UX-14 sin duplicarla a `Cipher`, `Authentication`, `Keys`, `Certificates` y `Generic`.

- `ModuleI18n` enlaza textos estáticos de controles visibles con `I18nService`.
- `ModuleTextCatalog` centraliza las claves por módulo.
- Se localizan títulos, subtítulos, ayudas, etiquetas, botones, prompts, menús, estados vacíos y una selección de estados de reset/cancelación.
- Los listeners se registran al crear cada módulo, por lo que la preferencia ES/EN se refleja al abrirlo y al cambiar idioma.
- El binding no reemplaza un valor que ya haya sido cambiado dinámicamente por una operación; esto protege resultados técnicos.

## Archivos principales

- `src/main/java/com/cryptocarver/ui/ModuleI18n.java`
- `src/main/java/com/cryptocarver/ui/ModuleTextCatalog.java`
- `src/main/java/com/cryptocarver/ui/{CipherController,AuthenticationController,KeysController,CertificatesController,GenericController}.java`
- `src/main/resources/fxml/{cipher,authentication,keys}.fxml`
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_es.properties`
- `src/test/java/com/cryptocarver/ui/ModuleTextCatalogTest.java`
- `src/test/java/com/cryptocarver/ui/ModuleI18nLiveUITest.java`

## Compatibilidad

No se añadieron dependencias ni se modificaron algoritmos, `OperationResult`, formatos, histórico, recetas o política de secretos. Los tokens técnicos (`AES`, `RSA`, `PKCS#11`, `TR-31`, `RFC`, `SHA-256`, `PEM`, HEX/Base64, JSON/XML y bytes) se conservan literalmente en las envolturas traducidas.

## Pruebas y verificación

- `mvn -q -Dtest=ModuleTextCatalogTest,I18nServiceTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest test` — OK.
- `xmllint --noout` — OK para `cipher.fxml`, `authentication.fxml`, `keys.fxml`, `certificates.fxml` y `generic.fxml`.
- `git diff --check` — OK.
- `ModuleI18nLiveUITest` cubre Cipher y Keys en vivo, pero está marcado como UI opt-in porque este entorno no dispone de una pantalla JavaFX.

## Limitaciones y deuda para UX-15B

Los mensajes dinámicos específicos de cada operación, diálogos Java y textos internos de submódulos incluidos requieren una segunda pasada por catálogo. `Generic` excluye explícitamente el pane `Process Designer`; su migración queda fuera de UX-15A. También quedan fuera History, Clipboard Shelf, Compare Results, PQC, XML/WSS, Payments, JOSE y demás módulos no prioritarios.
