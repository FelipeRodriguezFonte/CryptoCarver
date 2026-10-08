# Wallet fase 1 — mapa previo a extracción

Base de fase para GC: f0f99ed (caracterización fijada antes de extraer). Controller antes: 1003 líneas; tras extracción prevista: 907. Coordinador previsto: 260 líneas.

## Movimiento y conservación

Se trasladan las implementaciones: handleSdJwtIssue, handleSdJwtPresent, handleSdJwtVerify, handleSdJwtInspect, sdJwtReportForDisplay. Los manejadores FXML quedan como delegados privados de una línea. View es record de Supplier por control; Supplier<StatusReporter> lee el reporter actual; getter sdJwtCoordinator perezoso. FXML, fx:id y declaraciones de campos permanecen en WalletController.

View expone: sdJwtAlgoCombo, sdJwtIssuerKeyArea, sdJwtClaimsArea, sdJwtDisclosableArea, sdJwtVctField, sdJwtDecoyField, sdJwtIssueOutputArea, sdJwtPresentInputArea, sdJwtRevealArea, sdJwtAudienceField, sdJwtNonceField, sdJwtHolderKeyArea, sdJwtPresentOutputArea, sdJwtVerifyInputArea, sdJwtVerifyIssuerKeyArea, sdJwtVerifyHolderKeyArea, sdJwtVerifyAudienceField, sdJwtVerifyNonceField, sdJwtVerifyOutputArea, sdJwtInspectInputArea, sdJwtInspectOutputArea.

Helpers copiados a este propietario: t, parseInt, valueOf, textOf, isBlank, publish, fail, showValidation, updateStatus, logFailure, lines, blankToNull. Los helpers aún usados por otras secciones se quedan en el controlador. Se retiran del controlador únicamente: sdJwtReportForDisplay. Logger mantiene WalletController.class para conservar la identidad de logs. No se trasladan initialize, showSection, Clear ni Load Example: sus controles y claves de idioma siguen en el controlador. SD-JWT conserva el selector compartido con SCA/OID4VP.

Dependencias del coordinador: com.cryptocarver.model.OperationResult, javafx.scene.control.ComboBox, javafx.scene.control.TextArea, javafx.scene.control.TextField, org.slf4j.Logger, org.slf4j.LoggerFactory, java.nio.charset.StandardCharsets, java.util.List, java.util.function.Supplier, com.cryptocarver.crypto.JOSEService, com.nimbusds.jose.JWSAlgorithm, java.util.Arrays, com.cryptocarver.crypto.SdJwtOperations, com.cryptocarver.service.I18nService, java.util.ArrayList. Crypto solo se invoca, sin modificaciones. Las demás secciones mantienen sus dependencias.

## Caracterización y portabilidad

Test nuevo con shell y controles FXML reales, EN/ES, FULL_LAB/MASKED/REDACTED, casos válidos y validaciones, error estructural sin texto de excepción y sustitución de reporter tras construir el coordinador. SHA-256 se fija y se comprueba sobre producción sin extraer. Verificación criptográfica independiente. Se normalizan firmas, sales, digests aleatorios, iat/exp/validFrom/validUntil, coordenadas de claves y fechas; se ordenan claves JSON. No hay red. Estados e informes estables permanecen en la transcripción.

## Contratos de fuente y propietarios finales

Localizados SpecializedFeedbackHeadlessTest, SpecializedI18nTest, Ux19SpecializedHeadlessTest, ModernMainControllerFxmlStaticTest y WalletControllerDefaultsTest. El contrato de fuente FXML sigue en WalletController. SpecializedFeedbackHeadlessTest no tiene claves Wallet; no se reasigna ninguna aserción ni se modifica un test existente. Se ejecutan dirigidos después de extraer y antes de las puertas.

La tabla cubre las 143 claves module.wallet.* del bundle EN. Propiedad final prevista tras las tres extracciones; las claves compartidas conservan todos sus consumidores. Las etiquetas permanecen bajo el binding del controlador (ModuleTextCatalog). Los textos literales de fx:id y las claves no se renombran al sustituir referencias Java.

| Clave | Propietario(s) final(es) |
|---|---|
| module.wallet.adesHelp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.adesTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.build | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.describeList | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.encode | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.etsiReport | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.findCertificate | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.inspectOnly | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.resolveIndex | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.toJson | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.validate | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.btn.verifySignature | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.buildTransactionData | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.cborRequired | WalletController |
| module.wallet.cborTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.certificateRequired | WalletController, WalletMdocCoordinator |
| module.wallet.claimsRequired | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator |
| module.wallet.disclaimer | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.documentRequired | WalletController |
| module.wallet.eidasCertHelp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.eidasCertTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.errorTitle | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.fromJson | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.indexRequired | WalletStatusListCoordinator |
| module.wallet.inspect | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.inspectOid4vp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.issue | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.jsonRequired | WalletController |
| module.wallet.keyBindingIncomplete | WalletSdJwtCoordinator |
| module.wallet.keyRequired | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.lbl.certificatePEMOrBase64 | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.certificateToLookUp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.claimsToRevealOne | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.credentialIdentifiersCommaSeparated | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.credentialTypeVctOptional | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.devicePublicKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.documentSignerCertificatePEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.elementsJSONKeyedBy | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.expectedAudienceAndNonce | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.expectedAudienceNonceAnd | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.fileNameDSSPicks | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.holderPrivateKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.holderPublicKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.issuerPrivateKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.issuerPublicKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.keyBindingAudienceAnd | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.listURIGoesIn | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.presentationNoKeyNeeded | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.presentationSDJWTVC | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.requestObjectASigned | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.selectivelyDisclosablePathsOne | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.signedDocumentBase64Or | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.statusesCommaOrWhitespace | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.transactionDataEntriesOne | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.lbl.verifierPublicKeyPEM | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.loadExample | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.mdocHelp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.mdocRequired | WalletMdocCoordinator |
| module.wallet.mdocTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.notInTrustedList | WalletController |
| module.wallet.operation | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.present | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.privateJwkHidden | WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.requestRequired | WalletController |
| module.wallet.resolve | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.scaHelp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.scaTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.sdJwtRequired | WalletController, WalletSdJwtCoordinator |
| module.wallet.sdJwtTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.status.cleared | WalletController |
| module.wallet.status.converted | WalletController |
| module.wallet.status.done | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.status.exampleLoaded | WalletController |
| module.wallet.status.failed | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.status.inspected | WalletController, WalletSdJwtCoordinator, WalletStatusListCoordinator |
| module.wallet.status.issued | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator, WalletStatusListCoordinator |
| module.wallet.status.presented | WalletSdJwtCoordinator |
| module.wallet.status.resolved | WalletStatusListCoordinator |
| module.wallet.status.verified | WalletController, WalletSdJwtCoordinator, WalletMdocCoordinator |
| module.wallet.statusListTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.statusesRequired | WalletStatusListCoordinator |
| module.wallet.subtitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.tokenRequired | WalletStatusListCoordinator |
| module.wallet.trustedEntityListJson | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedEntityListJsonInspect | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedEntityListJsonLabel | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedEntityListJsonPrompt | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedEntityListRequired | WalletController |
| module.wallet.trustedEntityListSearchCertLabel | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedEntityListSignerCertLabel | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedListHelp | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.trustedListRequired | WalletController |
| module.wallet.trustedListTitle | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.certificateMatch | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.certificateMatches | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profile | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileAnnex | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileNotEvaluated | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileNotEvaluatedReason | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileSatisfied | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileUnmet | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileUnrecognized | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.profileUnspecified | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.compactRequired | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.contact | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.historyCertAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.historyPeriod65535 | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.historyPeriodAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.historySki | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.identityCerts | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.invalidDates | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.nextUpdate | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.pointersAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.publicEaaCertConsistency | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.publicEaaInvalidCertificate | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.publicEaaOrganization | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.publicEaaStatus | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.publicEaaStatusSince | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.schemeInfoUri | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.schemeRules | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.serviceType | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.signatureRequired | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.statusAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.statusApproach | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.statusSinceAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.supplyPoint | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.territory | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.rule.version | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signature | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureAbsent | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureIndeterminate | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureInvalid | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureMissingInJws | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureSignerMismatch | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureUnverified | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.signatureValid | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.sinceUnspecified | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.statusImplicit | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.ts119602.statusUnspecified | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.uriRequired | WalletStatusListCoordinator |
| module.wallet.verify | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.verifyAndInspect | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
| module.wallet.verifyDynamicLinking | WalletController.moduleI18n / ModuleTextCatalog (etiqueta FXML) |
