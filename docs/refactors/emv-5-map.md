# EMV fase 5: Secure Messaging

Base e0027379; controlador inicial: 988 líneas. Borrador backup/emv-2-wip consultado únicamente con git show; no merge ni cherry-pick.

## Métodos y dependencias

Se mueven handleSmLoadExample, handleSmEncipherPin, handleSmGenerateMac, smVisa, smShow y smPublish a EmvSecureMessagingCoordinator. smText se copia como helper privado; la versión del controlador se queda para HCE/Data Storage. El coordinador utiliza EmvSecureMessaging, OperationDetail/OperationResult e I18nService, record View con Supplier de controles y Supplier<StatusReporter>. Getter perezoso y delegados de una línea en EMVController.

Se quedan handleSmDeriveSessionKeys y la derivación en EmvSessionKeyCoordinator; su propiedad no cambia. Se quedan FXML, initialize, constantes SM_MASTERCARD/SM_VISA, mainController e integración ModuleI18n, estado de módulo, perfil y clear. Todos los campos siguientes permanecen declarados en EMVController y se prestan mediante suppliers (no se renombran ids ni literales):

`smSchemeCombo`, `smMkSmiField`, `smMkSmcField`, `smPanSeqField`, `smUdkSmiField`, `smUdkSmcField`, `smAcField`, `smCommandNumberField`, `smAtcField`, `smSkMacField`, `smSkEncField`, `smPinField`, `smUdkAField`, `smHeaderField`, `smDataField`, `smResultArea`.

## Tests de claves en fuente

SpecializedFeedbackHeadlessTest comprueba module.emv.feedback.dolFormat en EMVController; se queda allí. sessionRequired sigue en EmvSessionKeyCoordinator; arqcRequired/arqcAmountRequired/arqcValid en EmvArqcCoordinator; arpcRequired/arpcSessionKeyLength en EmvArpcCoordinator; trackRequired/trackDataRequired en EmvTrack2Coordinator. Ninguna clave de ese test cambia de propietario. EmvOdaPaneTranslationTest comprueba bundles, no fuente por clase; los tests de panes comprueban los ids y handlers que siguen en EMVController. Se ejecutarán solos antes de las puertas. No hay modificación de tests existentes prevista.

## Propietario final de cada clave module.emv.*

Esta tabla enumera todas las claves del bundle, incluso las declarativas y las fases pendientes. Las compartidas indican ambos consumidores; hce.hexLength permanece en EMVController para Data Storage y también en EmvHceCoordinator para HCE. sm.status y sm.error permanecen además en EmvSessionKeyCoordinator. Se verifica por claves individuales, sin desplazar consumidores que se quedan.

| Clave | Propietario final |
| --- | --- |
| `module.emv.amount` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.amountOther` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.arc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.arpcTitle` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.arqc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.arqcTitle` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.atc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.atcInfo` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.buildDol` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.cap.action` | EMVController |
| `module.emv.cap.error` | EMVController |
| `module.emv.cap.exampleLoaded` | EMVController |
| `module.emv.cap.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.cap.result` | EMVController |
| `module.emv.cap.status` | EMVController |
| `module.emv.cap.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.clearStatus` | EMVController |
| `module.emv.country` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.csu` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.currency` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.date` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.decodeTrack` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.deriveIcc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.deriveKeys` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.deriveSession` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.discretionary` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.dolBuilder` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.amount` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.currency` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.digestAction` | EMVController |
| `module.emv.ds.digestResult` | EMVController |
| `module.emv.ds.dsUn` | EMVController |
| `module.emv.ds.dspkAction` | EMVController |
| `module.emv.ds.dspkResult` | EMVController |
| `module.emv.ds.error` | EMVController |
| `module.emv.ds.exampleLoaded` | EMVController |
| `module.emv.ds.gac` | EMVController |
| `module.emv.ds.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.id` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.idInvalid` | EMVController |
| `module.emv.ds.input` | EMVController |
| `module.emv.ds.operatorId` | EMVController |
| `module.emv.ds.prompt.amount` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.byte` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.currency` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.gac` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.id` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.input` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.operatorId` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.summary1` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.prompt.un` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.rcp` | EMVController |
| `module.emv.ds.status` | EMVController |
| `module.emv.ds.step1` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.step2` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.step3` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.summary1` | EMVController |
| `module.emv.ds.summaryAction` | EMVController |
| `module.emv.ds.summaryResult` | EMVController |
| `module.emv.ds.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.ds.un` | EMVController |
| `module.emv.encodeTrack` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.error.arpcSessionKeyLength` | EmvArpcCoordinator |
| `module.emv.error.arqcSessionKeyLength` | EmvArqcCoordinator |
| `module.emv.error.arqcUnFormat` | EmvArqcCoordinator |
| `module.emv.error.atcFormat` | EmvSessionKeyCoordinator |
| `module.emv.error.dol` | EMVController |
| `module.emv.error.generate` | EmvArpcCoordinator, EmvArqcCoordinator, EmvSessionKeyCoordinator, EmvTrack2Coordinator |
| `module.emv.error.generateFirst` | EmvArqcCoordinator |
| `module.emv.error.generic` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.error.imkLength` | EmvSessionKeyCoordinator |
| `module.emv.error.panDigits` | EmvSessionKeyCoordinator |
| `module.emv.error.required` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.error.sessionInputs` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.error.tlv` | EMVController |
| `module.emv.error.verification` | EmvArqcCoordinator |
| `module.emv.feedback.arpcRequired` | EmvArpcCoordinator |
| `module.emv.feedback.arqcAmountRequired` | EmvArqcCoordinator |
| `module.emv.feedback.arqcInvalid` | EmvArqcCoordinator |
| `module.emv.feedback.arqcRequired` | EmvArqcCoordinator |
| `module.emv.feedback.arqcValid` | EmvArqcCoordinator |
| `module.emv.feedback.dolFormat` | EMVController |
| `module.emv.feedback.sessionRequired` | EmvSessionKeyCoordinator |
| `module.emv.feedback.trackDataRequired` | EmvTrack2Coordinator |
| `module.emv.feedback.trackRequired` | EmvTrack2Coordinator |
| `module.emv.generateArpc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.generateArqc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.aip` | EmvHceCoordinator |
| `module.emv.hce.amount` | EmvHceCoordinator |
| `module.emv.hce.atc` | EmvHceCoordinator |
| `module.emv.hce.chip` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.counter` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.counterInvalid` | EmvHceCoordinator |
| `module.emv.hce.country` | EmvHceCoordinator |
| `module.emv.hce.currency` | EmvHceCoordinator |
| `module.emv.hce.cvr` | EmvHceCoordinator |
| `module.emv.hce.date` | EmvHceCoordinator |
| `module.emv.hce.deviceType` | EmvHceCoordinator |
| `module.emv.hce.error` | EmvHceCoordinator |
| `module.emv.hce.exampleLoaded` | EmvHceCoordinator |
| `module.emv.hce.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.hexLength` | EMVController, EmvHceCoordinator |
| `module.emv.hce.hours` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.hoursInvalid` | EmvHceCoordinator |
| `module.emv.hce.luk` | EmvHceCoordinator |
| `module.emv.hce.lukAction` | EmvHceCoordinator |
| `module.emv.hce.lukResult` | EmvHceCoordinator |
| `module.emv.hce.msdAction` | EmvHceCoordinator |
| `module.emv.hce.msdResult` | EmvHceCoordinator |
| `module.emv.hce.otherAmount` | EmvHceCoordinator |
| `module.emv.hce.prompt.aip` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.amount` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.atc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.counter` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.country` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.currency` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.cvr` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.date` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.deviceType` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.hours` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.luk` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.otherAmount` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.tvr` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.type` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.udk` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.un` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.prompt.year` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.qvsdcAction` | EmvHceCoordinator |
| `module.emv.hce.qvsdcResult` | EmvHceCoordinator |
| `module.emv.hce.status` | EmvHceCoordinator |
| `module.emv.hce.step1` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.step2` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.step3` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.terminal` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.tvr` | EmvHceCoordinator |
| `module.emv.hce.type` | EmvHceCoordinator |
| `module.emv.hce.udk` | EmvHceCoordinator |
| `module.emv.hce.un` | EmvHceCoordinator |
| `module.emv.hce.year` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.hce.yearInvalid` | EmvHceCoordinator |
| `module.emv.iccData` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.iccDn.action` | EMVController |
| `module.emv.iccDn.error` | EMVController |
| `module.emv.iccDn.exampleLoaded` | EMVController |
| `module.emv.iccDn.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.iccDn.result` | EMVController |
| `module.emv.iccDn.status` | EMVController |
| `module.emv.iccDn.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.iccMethod.a` | EMVController |
| `module.emv.iccMethod.auto` | EMVController |
| `module.emv.iccMethod.b` | EMVController |
| `module.emv.iccMethod.label` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.iccResult.decimalized` | EmvSessionKeyCoordinator |
| `module.emv.iccResult.input` | EmvSessionKeyCoordinator |
| `module.emv.iccResult.method` | EmvSessionKeyCoordinator |
| `module.emv.iccResult.sha1` | EmvSessionKeyCoordinator |
| `module.emv.iccResult.y` | EmvSessionKeyCoordinator |
| `module.emv.imk` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.inspectTlv` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.method` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.caExponent` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.caModulus` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.chain` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.cid` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.clear` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.error` | EmvOdaCoordinator |
| `module.emv.oda.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.iccCertificate` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.iccExponent` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.iccRemainder` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.issueTestCard` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.issuerCertificate` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.issuerExponent` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.issuerRemainder` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.noIssuerKey` | EmvOdaCoordinator |
| `module.emv.oda.pan` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.recoverKeys` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.sdad` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.signatures` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.ssad` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.staticData` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.status` | EmvOdaCoordinator |
| `module.emv.oda.terminalData` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.testCardIssued` | EmvOdaCoordinator |
| `module.emv.oda.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.transactionData` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.verifyCda` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.verifyDda` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.oda.verifySda` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.padding` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.panSeq` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.propAuth` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.rawTerminal` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.resetStatus` | EMVController |
| `module.emv.sessionKey` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sessionTitle` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.commandInvalid` | EmvSessionKeyCoordinator |
| `module.emv.sm.commandNumber` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.data` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.deriveSessionKeys` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.encipherPin` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.error` | EmvSecureMessagingCoordinator, EmvSessionKeyCoordinator |
| `module.emv.sm.exampleLoaded` | EmvSecureMessagingCoordinator |
| `module.emv.sm.generateMac` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.header` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.help` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.loadExample` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.mkSmc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.mkSmi` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.newPin` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.panSeq` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.pinInvalid` | EmvSecureMessagingCoordinator |
| `module.emv.sm.prompt.ac` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.data` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.header` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.mkSmc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.mkSmi` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.panSeq` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.pin` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.sessionKey` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.udkA` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.udkSmc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.prompt.udkSmi` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.scheme` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.skEnc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.skMac` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.status` | EmvSecureMessagingCoordinator, EmvSessionKeyCoordinator |
| `module.emv.sm.step1` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.step2` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.step3` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.title` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.udkA` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.udkSmc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.sm.udkSmi` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.status` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.status.arpc` | EmvArpcCoordinator |
| `module.emv.status.arqc` | EmvArqcCoordinator |
| `module.emv.status.dol` | EMVController |
| `module.emv.status.session` | EmvSessionKeyCoordinator |
| `module.emv.status.tlv` | EMVController |
| `module.emv.status.trackDecoded` | EmvTrack2Coordinator |
| `module.emv.status.trackEncoded` | EmvTrack2Coordinator |
| `module.emv.tlvInput` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.tlvTitle` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.track2.invalid` | EmvTrack2Coordinator |
| `module.emv.trackInput` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.trackTitle` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.transactionFields` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.tvr` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.type` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.un` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
| `module.emv.verifyArqc` | ModuleTextCatalog.emv / ModuleI18n (UI declarativa) |
