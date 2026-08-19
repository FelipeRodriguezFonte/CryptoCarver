# UX-22 — handoff

## Módulos auditados

- Cipher y Authentication: readiness preflight, primer campo no válido, foco y resumen de ejecución.
- Keys/Certificates y TR-31: validaciones de KBPK, key block, certificados/KeyStore y estados TR-31.
- XML/XAdES y WSS: entrada XML/SOAP, TSA, KeyStore y alias con error inline y foco asociado.
- Payments/EMV/DUKPT/PIN: se conserva el catálogo específico existente; las rutas principales de PIN, PVK/PVV y DUKPT dirigen el foco al control de entrada.
- JOSE, ASN.1, CMS/PAdES/ASiC-S, OpenPGP, Hashing, Manual Conversion y Batch Runner: se revisaron los contratos de feedback existentes y sus claves localizadas; no se modificaron los valores técnicos ni los artefactos.

## Errores corregidos

- El panel de preflight de Modern UI traduce en vivo título, resumen, causa y remedio para entradas vacías, hexadecimal/Base64, algoritmo, modo, clave, IV/nonce, tag y firma.
- `focusControl` cubre ahora Cipher, Authentication, Certificates, Keys, XML, WSS, Payments, EMV y JOSE.
- `ModernMainController.showError(UserFacingError)` enfoca automáticamente el control asociado y mantiene el resaltado inline existente.
- WSS, XML/XAdES, Payments y TR-31 usan el error compartido con `fieldKey`; los mensajes se redactan antes de feedback visible o copia técnica.
- TR-31 deja de mostrar errores fijos de entrada/estado y usa claves ES/EN específicas sin alterar key blocks, KSN, bytes ni resultados.
- Corregido el fallback aislado de TR-31: `showTR31Validation` recibe el `TextArea` de destino de forma explícita; exportación usa `tr31ExportResultArea`, mientras importación y parseo de cabecera usan `tr31ImportResultArea`. Con `mainController` presente se conserva el banner compartido y su `fieldKey`.
- Las excepciones de esas tres rutas conservan la traza técnica, pero se escriben en `stderr` después de `InlineErrorPresenter.redactSecrets(...)`; no se eliminó ni se cambió el logging global.
- Se añadió feedback localizado para copiar informes técnicos redactados.

## Pruebas

- `Ux22HeadlessTest` — claves ES/EN, fallback seguro, redacción de secretos y preservación de TR-31, hashes y bytes.
- `Ux22HeadlessTest.isolatedTR31FallbackKeepsExportImportAndParseFeedbackSeparated` — instancia `KeysController` sin `mainController`, invoca por reflexión limitada el fallback de exportación, importación y parseo, y comprueba destinos separados, `visible`, `managed` y redacción.
- `Ux22ValidationLiveUITest` — opt-in JavaFX para foco en Cipher, XML, WSS, Payments y TR-31, además de cambio de idioma del banner.
- `mvn -q -Dtest=Ux22HeadlessTest test` ✅
- `mvn -q test` ✅
- `git diff --check` ✅; el entorno imprime únicamente el warning preexistente del fsmonitor IPC.
- No se modificó FXML; por ello no se ejecutó `xmllint` para esta corrección.

## Limitación JavaFX

La prueba live requiere `-DrunUiTests=true` y `xvfb-run`. El entorno local macOS no dispone de Xvfb ni de un toolkit JavaFX gráfico utilizable en el comando headless; por eso la regresión nueva usa un sink privado package-private que modela las tres mutaciones de un `TextArea` (`setText`, `setVisible`, `setManaged`). La UI opt-in no se ejecutó localmente.

## Deuda restante

- Hay validaciones legacy fuera de Modern UI que todavía escriben texto en áreas de resultado en vez de usar el banner compartido, especialmente algunas rutas profundas de PIN, CMS y utilidades antiguas.
- Los mensajes dinámicos de excepciones de terceros requieren revisión continua para evitar detalles sensibles; los puntos auditados pasan por redacción antes de mostrarse.
- Conviene ampliar la matriz UI live a cada variante de operación y locale en futuras iteraciones.
- La regresión headless no crea controles JavaFX reales; la asignación a los `TextArea` concretos queda garantizada por los argumentos explícitos en las ocho llamadas de producción y debe cubrirse en CI/Xvfb con la prueba UI opt-in.

No se ha creado ningún commit ni se ha hecho push.
