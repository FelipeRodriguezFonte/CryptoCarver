# XML Signature — fase 0: mapa de privacidad

Base: `55c8247c9ec749e3ac293779c5d0087c385e4572`. Controlador: 747 líneas.
Worktree: `/Users/feliperodriguezfonte/dev/CryptoCarver-xmlsig-1`, rama `codex/xmlsig-1`.

## Cobertura existente leída antes de empezar

`XMLSignatureControllerUITest` carga `xml_security.fxml` mediante `UiTestFxml` y prueba el ToggleGroup local/PKCS#11 y la visibilidad/managed del formulario local completo. Usa reflexión para los controles inyectados y llamadas a `RadioButton.fire()`. No firma, verifica, inspecciona, carga almacenes ni prueba TSA; no tiene inyección de selectores de fichero. No se modifica.
`XMLSignatureOperationsTest` prueba firma B, verificación con truststore e inspección, pero sin shell ni política de privacidad. No sustituye una auditoría UI.

## Manejadores y controles

Todos los nombres siguientes son fx:id y campos del controlador originales. Ninguno cambia.

| Manejador | Campos FXML leídos/escritos | Resultados/superficies |
|---|---|---|
| handleBrowseXMLSignInput | xmlSignInputPathField | Ruta en control; diálogo de apertura |
| handleBrowseXMLKey, handleLoadXMLKeys | xmlSignSourcePkcs11Radio, xmlSignKeyPathField, xmlSignKeyPasswordField, xmlSignKeyAliasCombo | Alias en combo, estado o error; crypto carga material local o sesión PKCS#11 |
| handleSignXML | xmlSignInputPathField, xmlSignLevelCombo, xmlSignPackagingCombo, xmlSignKeyPathField, xmlSignKeyPasswordField, xmlSignKeyAliasCombo, xmlSignSourcePkcs11Radio; controles TSA abajo | XML en xmlSignOutputArea; OperationResult (entrada XML, salida firmada, detalles públicos de origen/ruta/tamaño); inspector, historial/receta, Shelf y expandido mediante shell |
| handleVerifyXML | xmlVerifyInputArea, xmlVerifyTrustStorePathField, xmlVerifyTrustStorePasswordField, xmlVerifyReportArea | Informe en control y OperationResult; inspector, historial/receta, Shelf, expandido; exportación SimpleReport.xml, DetailedReport.xml, ETSIReport.xml mediante DirectoryChooser |
| handleBrowseXMLInspectorInput, handleInspectSignedXML | xmlInspectInputArea, xmlInspectReportArea | XML cargado; informe estructural y OperationResult; estado, inspector, historial/receta, Shelf, expandido |
| handleSaveSignedXML | xmlSignOutputArea | signed.xml mediante SaveDialog; OperationResult con salida XML y ruta; superficies del shell |
| handleBrowseXMLTrustStore, handleLoadXMLTrustStoreProfile | xmlVerifyTrustStorePathField, xmlVerifyTrustStorePasswordField, xmlVerifyTrustStoreProfileCombo | Ruta en control, contraseña borrada al cargar perfil, estado |
| handleTestTSA | xmlSignTsaUrlText, xmlSignTsaAuthTypeCombo, xmlSignTsaUserField, xmlSignTsaPasswordField | Validación URL, persiste custom TSA antes de petición asíncrona; informe diagnóstico/error; red real (no se ejecuta en auditoría) |
| handleSaveTSA | xmlSignTsaUrlText | customTsaUrl persistida y showInfo con URL |
| handleLoadTSASavedProfile | xmlSignTsaProfileCombo, xmlSignTsaUrlText, xmlSignTsaProfileNameField | URL y nombre en controles; estado |
| handleSaveTSASavedProfile | xmlSignTsaUrlText, xmlSignTsaProfileNameField, xmlSignTsaProfileCombo | tsaProfiles[].url/name y customTsaUrl persistidos; showInfo con URL |
| handleDeleteTSASavedProfile | xmlSignTsaProfileCombo, xmlSignTsaProfileNameField | Perfil retirado de ajustes, combos actualizados, estado |
| handleBrowseTimestampFile, handleRequestTimestamp | xmlTimestampFileField, xmlTimestampUrlField, xmlTimestampHashCombo, xmlTimestampReportArea | customTsaUrl persistida; bytes lastTimestampToken internos; informe visible, OperationResult y superficies shell; petición de red real (no se ejecuta) |
| handleSaveTimestampToken | lastTimestampToken (no FXML) | Fichero timestamp.tsr vía SaveDialog; estado/error |
| handleBrowseTimestampToken, handleInspectTimestampToken | xmlTimestampTokenField, xmlTimestampFileField, xmlTimestampReportArea | Informe con certificado, cadena, imprint, fecha, serial y correspondencia con fichero; error/estado, sin publicación OperationResult |
| handleValidateTimestampToken | xmlTimestampTokenField, xmlTimestampFileField, xmlTimestampTrustStoreField, xmlTimestampTrustStorePasswordField, xmlTimestampReportArea | Informe de validación visible; estado/error, sin publicación OperationResult |
| handleBrowseTimestampTrustStore | xmlTimestampTrustStoreField | Ruta en control |
| initialize, handleXMLSignSourceChanged, handleReset, handleClear | xmlSecurityContainer, xmlAccordion, xmlSignSourceLocalRadio, xmlSignSourcePkcs11Radio, xmlSignSourceToggleGroup, xmlSignLocalKeyBox, xmlSignKeyPathLabel, xmlSignKeyPasswordLabel y controles anteriores | Binding i18n; defaults/visibilidad; limpieza de texto, aliases y token interno. Permanecen en controlador |

`initModule` conserva StatusReporter; `expandAccordionPane` y `fillClipboardInput` gestionan navegación/entrada. Helpers: t, showValidationError, getTsaCredentials, getTsaUrl, isHttpUrl, isPresetTsa, saveCustomTsa, reloadTsaProfiles, chooseFile, extractReportValue. El alias histórico `xmlSignTsaUrlCombo -> xmlSignTsaUrlText` en UiStateSnapshot debe conservarse.

## Entradas secretas y límites de superficies

- Contraseña PKCS#12/JKS/PEM: xmlSignKeyPasswordField. Clave privada cargada desde fichero o sesión: vive en crypto/sesión, no es un campo FXML. Deben buscarse PEM, base64 DER y bytes privados hex en salidas y persistencia; no confundir valor interno de PasswordField con texto visible.
- Contraseñas de confianza: xmlVerifyTrustStorePasswordField, xmlTimestampTrustStorePasswordField.
- Credenciales TSA: xmlSignTsaUserField y xmlSignTsaPasswordField; AuthType selecciona NONE/BASIC/BEARER. getTsaCredentials crea material efímero, no lo guarda expresamente. Las URLs editables xmlSignTsaUrlText y xmlTimestampUrlField también pueden contener credenciales en user-info; isHttpUrl admite host http(s) sin rechazar user-info.
- AppSettings persiste en fichero JSON (en esta base, no java.util.prefs). Un perfil guarda nombre/URL; saveCustomTsa guarda URL. Ambos caminos requieren inspección del fichero real, no solo del objeto en memoria.
- OperationResult alimenta el shell; las recetas se capturan desde controles mediante UiStateSnapshot. MASKED/REDACTED redactan campos sensibles; FULL_LAB permite conservar entradas en recetas. Contraseñas de entrada en PasswordField siguen siendo internas; no se enumeran como salida visible por leer getText().
- Los informes estructurales de XML y TSA contienen certificados públicos; no son por ello claves privadas. El token firmado es público salvo contenido incrustado secreto.
- Shelf y visor expandido capturan la salida actual; historial contiene datos y recetas. Los ficheros de exportación no pasan necesariamente por la política de presentación del shell.

## Plan de caracterización y condición de parada

Primero reproducir el guardado de perfil TSA con URL de autenticación inventada sobre shell y controles FXML reales, en ajustes aislados. Comprobar el fichero settings.json y recarga desde disco, además de controles de perfil. Sin resolver host ni contactar TSA. FULL_LAB conserva el comportamiento original.
Si se confirma persistencia de credenciales en MASKED/REDACTED, detener inmediatamente según fase 0(c): las operaciones posteriores (carga/firma/verificación/inspección/token generado en memoria, guardados, superficies completas) quedan pendientes, sin simular cobertura. No abrir diálogos ni añadir hooks de producción para sortear el bloqueo. El test nuevo no sustituye las aserciones existentes ni relaja umbrales.

## Continuación autorizada: corrección y nueva parada

La continuación del encargo autoriza sanear user-info en AppSettings para todos los perfiles, también FULL_LAB. `setCustomTsaUrl` y `saveTsaProfile` usan el mismo helper; `load` sanea los endpoints antiguos en memoria. Los campos XML siguen conservando la URL completa para la operación actual. `saveCustomTsa` muestra `module.xml.tsaCredentialsNotSaved` mediante `StatusReporter.showInfo` antes de guardar; este punto cubre Save TSA, Save profile, Test TSA, firma con TSA y Request timestamp. Clave nueva en messages.properties y variantes EN/ES; propietaria XMLSignatureController, sin reasignar claves existentes.

La nueva auditoría avanza hasta carga de clave real inventada y firma XAdES-BASELINE-B: se cargan PKCS#12/alias, se seleccionan credenciales BASIC separadas y una URL con user-info, y se comprueban salida, estado, inspector, historial/receta, Shelf y expandido. La firma B no pide sello de tiempo ni resuelve la URL TSA.

**Nueva fuga:** `handleSignXML` introduce `details.put("TSA", tsaUrl)` sin clasificación secreta. El historial persiste la URL completa en `[0].details`, `[0].structuredDetails` y `[0].parameters["XMLSignatureController.xmlSignTsaUrlText"]`. En MASKED y REDACTED también llega al texto del inspector (`#inspectorPanel`, detalle TSA). Se confirma leyendo history.json temporal, no solo el OperationResult interno. El aviso y el saneamiento de settings.json sí pasan.

Por la instrucción de parar ante otra fuga, quedan pendientes verificación, inspección de XML y tokens, truststores y exportaciones. No se alteran UiStateSnapshot ni la clasificación/publicación de detalles para corregir esta nueva fuga. El alias xmlSignTsaUrlCombo -> xmlSignTsaUrlText permanece intacto.
