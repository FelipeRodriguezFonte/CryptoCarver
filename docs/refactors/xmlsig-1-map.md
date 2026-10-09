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
