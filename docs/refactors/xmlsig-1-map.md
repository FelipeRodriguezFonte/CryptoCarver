# Fase 1 — firma, verificación e inspección XML

## Superficie y propiedad

El alcance previsto para la extracción era únicamente `handleSignXML`, `handleVerifyXML`, `handleInspectSignedXML` y `handleSaveSignedXML` de `XMLSignatureController` a `XmlSignatureSigningCoordinator`. El controlador habría conservado los cuatro puntos FXML como delegados, y `initialize`, `handleReset`, `handleClear` y `handleXMLSignSourceChanged` permanecerían aquí. La extracción se retiró después de que G1 detectara una dependencia de test existente del propietario fuente de una clave de idioma.

| Manejo | Campos FXML | Entradas sensibles | Dependencias trasladadas |
|---|---|---|---|
| Sign XML | `xmlSignInputPathField`, `xmlSignLevelCombo`, `xmlSignPackagingCombo`, `xmlSignTsaUrlText`, `xmlSignSourcePkcs11Radio`, `xmlSignKeyPathField`, `xmlSignKeyPasswordField`, `xmlSignKeyAliasCombo`, `xmlSignTsaAuthTypeCombo`, `xmlSignTsaUserField`, `xmlSignTsaPasswordField`, `xmlSignOutputArea` | Contraseña PKCS#12, clave privada cargada, autenticación TSA separada; URL TSA saneada al publicar por regla A | `XMLSignatureOperations`, `AppSettings`, `TsaAuthCredentials`, validación FXML, reporter, saneador compartido; la carga de alias queda en fase 2 vía callback |
| Verify XML | `xmlVerifyInputArea`, `xmlVerifyReportArea`, `xmlVerifyTrustStorePathField`, `xmlVerifyTrustStorePasswordField` | Contraseña truststore y certificados de confianza | `XMLSignatureOperations.verifyXAdES`, construcción/publicación de resultado, presentación y exportación existente |
| Inspect Signed XML | `xmlInspectInputArea`, `xmlInspectReportArea` | XML firmado de entrada | `XMLSignatureOperations.inspectSignedXml`, reporter |
| Save Signed XML | `xmlSignOutputArea` | XML firmado de salida | `FileChooser`, `Files`, reporter |

Las claves `module.xml.*` usadas por estos cuatro métodos se mueven junto con su lógica; no se cambian claves, textos ni propietarios. `statusReporter` se inyecta perezosamente con `Supplier<StatusReporter>`. Un `record View` conecta los controles existentes y callbacks del controlador para funciones que pertenecen a otra fase (URL TSA, saneamiento/publicación, carga de claves). No se renombra ningún `fx:id` ni el alias de historial `xmlSignTsaUrlText`.

## Cobertura previa existente

`XMLSignatureControllerUITest` carga el FXML real, prueba el ToggleGroup local/PKCS#11 y que el formulario local cambia visibilidad/managed. No caracteriza operaciones de firma, verificación, exportación ni inspección. La auditoría adicional de fase 0 cubre firma/verify core, resultados exportables, handlers de inspección y validación token; `XMLSignatureSigningPrivacyUITest` cubre firma real y publicación. El test de caracterización de esta fase fija el contrato observable de firma/inspección sin diálogo ni red.

## Extracción rehecha (revisor): propietario de cada clave

La extracción a `XmlSignatureSigningCoordinator` se rehízo con la reasignación de propietarios autorizada en `SpecializedFeedbackHeadlessTest`. Solo cambia la clase asociada a cada clave; se mantienen la presencia en fuente y la paridad EN/ES.

| Clave | Antes | Después | Motivo |
|---|---|---|---|
| `module.xml.feedback.saveRequired` | XMLSignatureController | XmlSignatureSigningCoordinator | `handleSaveSignedXML` se mueve |
| `module.xml.feedback.statusInspected` | XMLSignatureController | XmlSignatureSigningCoordinator | `handleInspectSignedXML` se mueve |
| `module.xml.feedback.keyStoreRequired` | XMLSignatureController | XMLSignatureController y XmlSignatureSigningCoordinator | la usan `handleLoadXMLKeys` (se queda hasta la fase 2) y `handleSignXML` |
| `module.xml.feedback.aliasRequired` | XMLSignatureController | XMLSignatureController y XmlSignatureSigningCoordinator | ídem |

El resto de claves de la lista (TSA y sello de tiempo) siguen en el controlador hasta la fase 3. El test `concreteValidationFlowsDoNotUseTheOldGenericRequiredFeedback` incluye además el coordinador nuevo, para que la prohibición de `module.xml.error.required` siga cubriendo el código movido.

Dependencias que el coordinador recibe del controlador por `View` hasta las fases 2 y 3: URL de TSA, validación http(s), guardado de TSA personalizada, URL publicada sin user-info, credenciales TSA, carga de alias y presentación de errores de validación. `extractReportValue` se mueve con sus dos únicos usos.

Contratos ejecutados solos antes de las puertas: `SpecializedFeedbackHeadlessTest` y `ModernMainControllerFxmlStaticTest` (2 informes / 31 pruebas / 0 fallos); los cinco tests UI de XML (5 informes / 13 pruebas / 0 fallos), incluida la caracterización de fase 1 con su SHA-256 sin cambios.
