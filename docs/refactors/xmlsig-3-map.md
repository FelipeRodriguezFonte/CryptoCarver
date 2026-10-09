# Fase 3 — TSA, perfiles guardados y tokens RFC 3161

Base de la fase: `7cea3d2` (fase 2 extraída).

## Qué se mueve a `XmlSignatureTimestampCoordinator`

| Manejador | Campos FXML | Entradas sensibles | Dependencias |
|---|---|---|---|
| `handleTestTSA` | `xmlSignTsaUrlText`, `xmlSignTsaAuthTypeCombo`, `xmlSignTsaUserField`, `xmlSignTsaPasswordField` | credenciales TSA separadas; user-info de la URL | `TsaDiagnostics.timestamp` (red), reporter |
| `handleSaveTSA` | `xmlSignTsaUrlText` | user-info de la URL | `AppSettings.setCustomTsaUrl` |
| `handleLoadTSASavedProfile`, `handleSaveTSASavedProfile`, `handleDeleteTSASavedProfile` | `xmlSignTsaProfileCombo`, `xmlSignTsaProfileNameField`, `xmlSignTsaUrlText` | user-info de la URL | `AppSettings` (perfiles TSA) |
| `handleBrowseTimestampFile`, `handleBrowseTimestampToken` | `xmlTimestampFileField`, `xmlTimestampTokenField` | — | `chooseFile` del controlador |
| `handleRequestTimestamp` | `xmlTimestampFileField`, `xmlTimestampUrlField`, `xmlTimestampHashCombo`, `xmlTimestampReportArea` | user-info de la URL | `TsaDiagnostics.timestamp` (red), `OperationResult`, reporter |
| `handleSaveTimestampToken` | — | — | `FileChooser`, último token recibido |
| `handleInspectTimestampToken` | `xmlTimestampTokenField`, `xmlTimestampFileField`, `xmlTimestampReportArea` | — | `TsaDiagnostics.inspectToken`, `tokenMatchesData` |
| `handleValidateTimestampToken` | los anteriores más `xmlTimestampTrustStoreField`, `xmlTimestampTrustStorePasswordField` | contraseña del truststore | `TsaDiagnostics.validateToken` |

Se mueven con ellos el estado `lastTimestampToken` y los auxiliares de TSA: `getTsaUrl`, `isHttpUrl`, `isPresetTsa`, `hasTsaUserInfo`, `saveCustomTsa`, `publishedTsaUrl`, `reloadTsaProfiles` y `getTsaCredentials`, además de los tres literales de endpoints predefinidos. La regla A (URL publicada sin user-info fuera de FULL_LAB) y el aviso `module.xml.tsaCredentialsNotSaved` viajan dentro de `saveCustomTsa` y `publishedTsaUrl`, sin cambios.

## Qué se queda en el controlador

Los once puntos de entrada `@FXML` como delegados; `initialize`, `handleReset`, `handleClear`, `handleXMLSignSourceChanged`, `expandAccordionPane`, `fillClipboardInput`, `showValidationError`, `chooseFile`, `clearModuleData` y `restoreSafeDefaults`. `initialize` sigue poblando los combos y pide al coordinador `reloadTsaProfiles()`; `clearModuleData` le pide olvidar el último token. Las constantes `NO_TSA`, `DIGICERT_TSA` y `FREETSA_TSA` del controlador pasan a referenciar las del coordinador para que haya una sola definición.

`XmlSignatureSigningCoordinator` deja de recibir los auxiliares de TSA del controlador y los recibe del coordinador de sello de tiempo, a través de lambdas perezosas en su `View`.

## Propietario de cada clave en `SpecializedFeedbackHeadlessTest`

| Clave | Antes de la fase | Después |
|---|---|---|
| `module.xml.feedback.tsaProfileRequired` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.tsaRequestRequired` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.timestampFileRequired` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.timestampTokenRequired` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.timestampRequesting` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.timestampReceived` | XMLSignatureController | XmlSignatureTimestampCoordinator |
| `module.xml.feedback.timestampValidated` | XMLSignatureController | XmlSignatureTimestampCoordinator |

Tras la fase el controlador no conserva ninguna clave de esa lista, así que su entrada desaparece del mapa de claves del test; todas las claves siguen comprobadas en su nuevo propietario. `XMLSignatureController` permanece en la comprobación de que no se usa `module.xml.error.required`, junto con los tres coordinadores.

## Caracterización

`XMLSignaturePhase3CharacterizationUITest`, con el shell y el FXML reales, `AppSettings` aislado y un token RFC 3161 generado en el test por una TSA inventada en memoria. Sin red ni diálogos: `handleTestTSA` y `handleRequestTimestamp` solo se ejercitan por sus caminos de validación; `handleSaveTimestampToken` por el camino sin token; los Browse no se ejecutan.

Transcripción fijada (SHA-256 `ab9156a4c477a23f0e26ea50c398739f569269c95b0bb040af09c9400211de0c`): títulos de error e información, líneas de estado en inglés, contenido de los combos de perfil, persistencia en `AppSettings` y líneas estables del informe del token (cabecera, política, sujeto, EKU, coincidencia con el fichero y nota final). No se fijan fechas, números de serie, huellas, rutas ni textos de excepción.

Sin cobertura de extremo a extremo, por requerir red o diálogo: respuesta correcta de `handleTestTSA` y de `handleRequestTimestamp`, y guardado del token. Ese código se mueve literal.
