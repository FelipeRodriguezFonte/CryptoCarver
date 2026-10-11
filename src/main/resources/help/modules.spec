# module-help:op_auth_sig begin
@op_auth_sig authentication.fxml
sign: help_handleSign -> input, algorithm, private_key, output
verify: help_handleVerify -> input, algorithm, public_key, signature, output
load_private_key: help_handlePasteSignPrivateKey, help_handleLoadSignPrivateKey, sigPrivKeyShelfMenu -> private_key
load_public_key: help_handlePasteSignPublicKey, help_handleLoadSignPublicKey, sigPubKeyShelfMenu -> public_key
apply_template: help_handleApplySignatureTemplate, help_handleResetSignatureDefaults -> template, algorithm
.input: authInputArea
.algorithm: signatureAlgorithmCombo
.private_key: signaturePrivateKeyArea
.public_key: signaturePublicKeyArea
.signature: signatureVerifyField
.template: signatureTemplateCombo
.output: authOutputArea
# module-help:op_auth_sig end
# module-help:op_auth_mac begin
@op_auth_mac authentication.fxml
generate: help_handleGenerateMAC -> input, algorithm, output_length, key_source, key, nonce, output
verify: help_handleVerifyMAC -> input, algorithm, output_length, key_source, key, nonce, expected_mac, output
load_key: help_handlePasteMacKey, help_handleLoadMacKey, macKeyShelfMenu -> key
key_lab: macSaveToLabBtn, macInspectKeyBtn -> key_source, key
.input: authInputArea
.algorithm: authMacAlgorithmCombo
.output_length: authMacTruncationCombo
.key_source: macKeySourceCombo, macHsmKeyCombo
.key: authMacKeyField
.nonce: authMacNonceField
.expected_mac: authMacVerifyField
.output: authOutputArea
# module-help:op_auth_mac end
# module-help:op_keys_lab begin
@op_keys_lab keys.fxml
find:  -> search, status_filter, table
generate: help_handleKeyLabGenerate -> name, algorithm, size, kcv_length
import: keyLabImportBtn -> name, algorithm, import_bytes
save_metadata: help_handleKeyLabSaveMetadata, help_handleKeyLabCopyId -> detail_name, usages
reveal: keyLabRevealBtn -> value
use: keyLabUseCipherBtn, keyLabUseMacBtn -> table, usages
archive_delete: keyLabArchiveBtn, help_handleKeyLabDelete -> table
transfer_metadata: help_handleImportKeyLabMetadata, help_handleExportKeyLabMetadata -> table
.search: keyLabSearchField
.status_filter: keyLabStatusFilterCombo
.table: keyLabTable
.name: keyLabNewNameField
.algorithm: keyLabNewAlgoCombo
.size: keyLabNewSizeCombo
.import_bytes: keyLabImportBytesField
.detail_name: keyLabDetailNameField
.usages: keyLabUsageEncryptCheck, keyLabUsageDecryptCheck, keyLabUsageMacCheck, keyLabUsageWrapCheck, keyLabUsageUnwrapCheck
.value: keyLabDetailValueField
.kcv_length: useFourByteKcvCheck
# module-help:op_keys_lab end
# module-help:op_keys_gen begin
@op_keys_gen keys.fxml
generate: help_handleGenerateKey -> type, odd_parity, generated_key, kcv_length
save_to_lab: saveGeneratedKeyButton, saveGeneratedSummaryButton -> generated_key
copy: copyGeneratedKeyButton, copyGeneratedKcvButton, copyGeneratedSummaryButton -> generated_key
open_validation: openValidationButton -> generated_key
.type: keyTypeCombo
.odd_parity: forceOddParityCheck
.generated_key: generatedKeyField
.kcv_length: useFourByteKcvCheck
# module-help:op_keys_gen end
# module-help:op_keys_val begin
@op_keys_val keys.fxml
validate: help_handleValidateKey -> key, result, kcv_length
.key: keyInputField
.result: validationResultArea
.kcv_length: useFourByteKcvCheck
# module-help:op_keys_val end
# module-help:op_keys_share begin
@op_keys_share keys.fxml
split: help_handleSplitKey -> key, parts, result
combine: help_handleCombineComponents -> components, result
.key: keyToSplitField
.parts: numComponentsCombo
.components: component1Field, component2Field, component3Field, component4Field, component5Field
.result: componentResultsArea
# module-help:op_keys_share end
# module-help:op_keys_kdf begin
@op_keys_kdf keys.fxml
derive: help_handleDeriveKey -> algorithm, input_format, input, iterations, output_length, salt, info, result
generate_salt: help_handleGenerateKdfSalt -> salt
.algorithm: kdfAlgorithmCombo
.input_format: kdfInputFormatCombo
.input: kdfInputField
.iterations: kdfIterationsField
.output_length: kdfOutputLengthField
.salt: kdfSaltField, kdfSaltFormatCombo
.info: kdfInfoField, kdfInfoFormatCombo
.result: kdfResultArea
# module-help:op_keys_kdf end
# module-help:op_keys_wrap begin
@op_keys_wrap keys.fxml
execute: help_handleAesKeyWrap -> mode, direction, kek, data, result
.mode: keyWrapModeCombo
.direction: keyWrapUnwrapCheck
.kek: keyWrapKekField
.data: keyWrapDataField
.result: keyWrapResultArea
# module-help:op_keys_wrap end
# module-help:op_keys_tr31 begin
@op_keys_tr31 keys.fxml
export: help_handleTR31Export -> kbpk_export, key, version, usage, algorithm, mode, exportability, optional_blocks, export_result
import: help_handleTR31Import -> kbpk_import, key_block, expected_length, import_result
parse_header: help_handleTR31ParseHeader -> key_block, import_result
clear_reset: help_handleTR31Clear, help_handleTR31Reset -> version
.kbpk_export: tr31KbpkExportField
.key: tr31KeyToWrapField
.version: tr31VersionCombo
.usage: tr31UsageCombo
.algorithm: tr31AlgorithmCombo
.mode: tr31ModeCombo
.exportability: tr31ExportabilityCombo
.optional_blocks: tr31OptionalBlockCombo, tr31OptionalBlocksField
.export_result: tr31ExportResultArea
.kbpk_import: tr31KbpkImportField
.key_block: tr31KeyBlockField
.expected_length: tr31KeyLengthField
.import_result: tr31ImportResultArea
# module-help:op_keys_tr31 end
# module-help:op_keys_compare begin
@op_keys_compare keys.fxml
compare: help_handleCompareKeyPair -> public, private, result
.public: keyComparePublicArea
.private: keyComparePrivateArea
.result: keyCompareResultArea
# module-help:op_keys_compare end
# module-help:op_keys_material begin
@op_keys_material keys.fxml
inspect: help_handleInspectKeyMaterial -> input, report
.input: keyMaterialInputArea
.report: keyMaterialReportArea
# module-help:op_keys_material end
# module-help:op_keys_store begin
@op_keys_store keys.fxml
inspect: help_handleInspectKeyStore -> path, type, password, unsafe_extract, report
choose: help_handleChooseKeyStore -> path
profile: help_handleLoadKeyStoreProfile, help_handleSaveKeyStoreProfile -> profile, profile_name
.path: keyStorePathField
.type: keyStoreTypeCombo
.password: keyStorePasswordField
.unsafe_extract: keyStoreUnsafeExtractCheck
.profile: keyStoreProfileCombo
.profile_name: keyStoreProfileNameField
.report: keyStoreReportArea
# module-help:op_keys_store end
# module-help:op_keys_rsa begin
@op_keys_rsa keys.fxml
generate: rsaGenerateBtn -> size, public_key, private_key
copy_export: rsaCopyPublicBtn, rsaCopyPrivateBtn, rsaCopySummaryBtn, rsaExportPublicBtn, rsaExportPrivateBtn -> public_key, private_key
send_to_shelf: rsaSendShelfBtn, rsaSendPrivateShelfBtn -> public_key, private_key
use: rsaUseSignaturesBtn, rsaUseCertificatesBtn, rsaUseCipherBtn -> public_key, private_key
clear: rsaClearBtn -> public_key, private_key
.size: rsaKeySizeCombo
.public_key: rsaPublicKeyArea
.private_key: rsaPrivateKeyArea
# module-help:op_keys_rsa end
# module-help:op_keys_ecdsa begin
@op_keys_ecdsa keys.fxml
generate: help_handleGenerateECDSA -> size, public_key, private_key
copy_export: ecdsaCopyPublicBtn, ecdsaCopyPrivateBtn, ecdsaCopySummaryBtn, ecdsaExportPublicBtn, ecdsaExportPrivateBtn -> public_key, private_key
send_to_shelf: ecdsaSendShelfBtn, ecdsaSendPrivateShelfBtn -> public_key, private_key
use: ecdsaUseSignaturesBtn, ecdsaUseCertificatesBtn -> public_key, private_key
clear: ecdsaClearBtn -> public_key, private_key
.size: ecdsaCurveCombo
.public_key: ecdsaPublicKeyArea
.private_key: ecdsaPrivateKeyArea
# module-help:op_keys_ecdsa end
# module-help:op_keys_dsa begin
@op_keys_dsa keys.fxml
generate: dsaGenerateBtn -> size, public_key, private_key
copy_export: dsaCopyPublicBtn, dsaCopyPrivateBtn, dsaCopySummaryBtn, dsaExportPublicBtn, dsaExportPrivateBtn -> public_key, private_key
send_to_shelf: dsaSendShelfBtn, dsaSendPrivateShelfBtn -> public_key, private_key
use: dsaUseSignaturesBtn, dsaUseCertificatesBtn -> public_key, private_key
clear: dsaClearBtn -> public_key, private_key
.size: dsaKeySizeCombo
.public_key: dsaPublicKeyArea
.private_key: dsaPrivateKeyArea
# module-help:op_keys_dsa end
# module-help:op_keys_eddsa begin
@op_keys_eddsa keys.fxml
generate: help_handleGenerateEdDSA -> public_key, private_key
copy_export: eddsaCopyPublicBtn, eddsaCopyPrivateBtn, eddsaCopySummaryBtn, eddsaExportPublicBtn, eddsaExportPrivateBtn -> public_key, private_key
send_to_shelf: eddsaSendShelfBtn, eddsaSendPrivateShelfBtn -> public_key, private_key
use: eddsaUseSignaturesBtn, eddsaUseCertificatesBtn -> public_key, private_key
clear: eddsaClearBtn -> public_key, private_key
.public_key: eddsaPublicKeyArea
.private_key: eddsaPrivateKeyArea
# module-help:op_keys_eddsa end
# module-help:op_keys_atalla_akb begin
@op_keys_atalla_akb keys.fxml
generate: help_handleAtallaGenerate -> template, header_bytes, header, mfk, key, result
unwrap: help_handleAtallaUnwrap -> mfk, block, result
example: help_handleAtallaExample -> header
.template: atallaTemplateCombo
.header_bytes: atalla0Combo, atalla1Combo, atalla2Combo, atalla3Combo, atalla4Combo, atalla5Combo, atalla6Combo, atalla7Combo
.header: atallaHeaderField, atallaMeaningArea
.mfk: atallaMfkField
.key: atallaKeyField
.block: atallaBlockArea
.result: atallaResultArea
# module-help:op_keys_atalla_akb end
# module-help:op_keys_kex_rsa begin
@op_keys_kex_rsa keys.fxml
export: help_handleRsaKexExport -> recipient, key, export_profile, envelope, export_result
import: help_handleRsaKexImport -> private_key, wrapped, import_profile, import_result
clear_reset: help_handleRsaKexClear, help_handleRsaKexReset -> export_profile
.recipient: rsaKexRecipientPemArea
.key: rsaKexKeyToWrapField
.export_profile: rsaKexExportProfileCombo
.envelope: rsaKexIncludeEnvelopeCheck, rsaKexKidField, rsaKexKeyVersionField
.export_result: rsaKexExportResultArea
.private_key: rsaKexPrivateKeyArea
.wrapped: rsaKexWrappedDataArea
.import_profile: rsaKexImportProfileCombo
.import_result: rsaKexImportResultArea
# module-help:op_keys_kex_rsa end
# module-help:op_keys_tr34 begin
@op_keys_tr34 keys.fxml
distribute: help_handleTr34Distribute -> sender_private_key, sender_cert, receiver_cert, key, key_id, binding_nonce, envelope, distribute_result
receive: help_handleTr34Receive -> receiver_private_key, expected_sender_cert, distributed_data, challenge_nonce, receive_result
challenge: help_handleTr34GenerateChallenge -> challenge_nonce
clear_reset: help_handleTr34Clear, help_handleTr34Reset -> key
.sender_private_key: tr34SenderPrivateKeyArea
.sender_cert: tr34SenderCertArea
.receiver_cert: tr34ReceiverCertArea
.key: tr34KeyToDistributeField
.key_id: tr34KeyIdField
.binding_nonce: tr34BindingNonceField
.envelope: tr34IncludeEnvelopeCheck
.distribute_result: tr34DistributeResultArea
.receiver_private_key: tr34ReceiverPrivateKeyArea
.expected_sender_cert: tr34ExpectedSenderCertArea
.distributed_data: tr34DistributedDataArea
.challenge_nonce: tr34ChallengeNonceField
.receive_result: tr34ReceiveResultArea
# module-help:op_keys_tr34 end
# module-help:op_keys_pkcs11 begin
@op_keys_pkcs11 keys.fxml
connect: help_handleConnectPkcs11, help_handleDisconnectPkcs11 -> library, slot, pin, report
profile: help_handleSavePkcs11Profile, help_handleDeletePkcs11Profile, help_handleChoosePkcs11Library -> saved_profile, name, library
sign: help_handlePkcs11Sign, help_handlePkcs11Verify -> signing_key, signature_algorithm, data, signature
certificate: help_handleShowPkcs11Certificate, help_handleUpdatePkcs11CertificateChain -> certificate_alias, certificate
jwt: help_handleGeneratePkcs11Jwt -> signing_key, jwt_algorithm, jwt_payload, jwt_output
cms: help_handleGeneratePkcs11Cms -> signing_key, cms_data, cms_detached, cms_output
wrap: help_handlePkcs11Wrap -> wrapping_key, key_to_wrap, wrap_transformation, wrap_result
unwrap: help_handlePkcs11Unwrap -> unwrapping_key, unwrap_transformation, wrapped_data, unwrap_algorithm, unwrap_type, unwrap_result
.saved_profile: pkcs11ProfileCombo
.name: pkcs11NameField
.library: pkcs11LibraryField
.slot: pkcs11SlotField
.pin: pkcs11PinField
.report: pkcs11ReportArea
.signing_key: pkcs11SigningKeyCombo
.signature_algorithm: pkcs11SignatureAlgorithmCombo
.data: pkcs11DataArea
.signature: pkcs11SignatureArea
.certificate_alias: pkcs11CertificateAliasCombo
.certificate: pkcs11CertificateArea
.jwt_algorithm: pkcs11JwtAlgorithmCombo
.jwt_payload: pkcs11JwtPayloadArea
.jwt_output: pkcs11JwtOutputArea
.cms_data: pkcs11CmsDataArea
.cms_detached: pkcs11CmsDetachedCheck
.cms_output: pkcs11CmsOutputArea
.wrapping_key: pkcs11WrappingKeyCombo
.key_to_wrap: pkcs11WrapKeyCombo
.wrap_transformation: pkcs11WrapTransformationCombo
.wrap_result: pkcs11WrapResultArea
.unwrapping_key: pkcs11UnwrappingKeyCombo
.unwrap_transformation: pkcs11UnwrapTransformationCombo
.wrapped_data: pkcs11UnwrapDataArea
.unwrap_algorithm: pkcs11UnwrapAlgorithmField
.unwrap_type: pkcs11UnwrapTypeCombo
.unwrap_result: pkcs11UnwrapResultArea
# module-help:op_keys_pkcs11 end
# module-help:op_pay_thales_lmk begin
@op_pay_thales_lmk keys.fxml
encrypt: help_handleThalesEncrypt -> lmk, key_type, scheme, clear_key, component, result
decrypt: help_handleThalesDecrypt -> lmk, key_type, cryptogram, component, result
describe: help_handleThalesDescribe -> key_type, scheme, result
lookup: help_handleThalesLookup -> lmk, cryptogram, check_value, result
example: help_handleThalesLoadExample -> lmk
.lmk: thalesLmkField
.key_type: thalesKeyTypeField
.scheme: thalesSchemeCombo
.clear_key: thalesClearKeyField
.cryptogram: thalesCryptogramField
.check_value: thalesCheckValueField
.component: thalesComponentCheck
.result: thalesResultArea
# module-help:op_pay_thales_lmk end
# module-help:op_pay_thales_key_block begin
@op_pay_thales_key_block keys.fxml
inspect: help_handleKeyBlockInspect -> block, result
unwrap: help_handleKeyBlockUnwrap -> block, lmk, result
example: help_handleKeyBlockExample -> block
.block: keyBlockInputArea
.lmk: keyBlockLmkField
.result: keyBlockResultArea
# module-help:op_pay_thales_key_block end
# module-help:op_keys_icsf_token begin
@op_keys_icsf_token icsf_token.fxml
analyze: help_handleAnalyze -> origin, format, input, summary, detail
load_save: help_handleLoadBinary, help_handleSaveReport -> input, detail
examples: help_handleSampleAesFixed, help_handleSampleVariableCipher, help_handleSampleDesTriple, help_handleSamplePkaRsa -> input
clear_reset: help_handleClear, help_handleReset -> input, origin
.origin: icsfTokenOriginCombo
.format: icsfTokenFormatCombo
.input: icsfTokenInputArea
.summary: icsfTokenSummaryTable
.detail: icsfTokenDetailArea
# module-help:op_keys_icsf_token end
# module-help:op_keys_icsf_batch begin
@op_keys_icsf_batch icsf_batch.fxml
analyze: help_handleAnalyze -> origin, reading, input, statistics, findings, inventory, report
load: help_handleLoadFile, help_handleLoadSample -> input
save: help_handleSaveText, help_handleSaveCsv -> include_detail, report, inventory
filter: help_handleClearFilter -> filter, inventory, findings
clear_reset: help_handleClear, help_handleReset -> input, origin
.origin: icsfBatchOriginCombo
.reading: icsfBatchFormatCombo
.input: icsfBatchInputArea
.include_detail: icsfBatchDetailCheck
.statistics: icsfBatchStatisticsTable
.findings: icsfBatchFindingsTable, icsfBatchFindingDetailArea
.filter: icsfBatchFilterField
.inventory: icsfBatchInventoryTable
.report: icsfBatchReportArea
# module-help:op_keys_icsf_batch end
# module-help:op_keys_icsf_keywrap begin
@op_keys_icsf_keywrap icsf_keywrap.fxml
export: help_handleExport, help_handleClearExport -> export_key, export_kek, export_type, export_cv, export_variant, export_mode, export_rn, export_options, report
import: help_handleImport, help_handleClearImport -> import_input, import_kek, import_type, import_cv, import_variant, import_mode, report
inspect: help_handleInspect, help_handleClearInspect -> inspect_input, report
resolve: help_handleResolve, help_handleClearResolve -> resolve_input, resolve_kek, resolve_expected_key, resolve_expected_kcv, resolve_type, report
result_actions: help_handleSaveReport, help_handleCopyToken, help_handleCopyKey, help_handleReset -> report
.export_key: icsfExportKeyField
.export_kek: icsfExportKekField
.export_type: icsfExportTypeCombo
.export_cv: icsfExportCvField
.export_variant: icsfExportVariantCombo
.export_mode: icsfExportModeCombo
.export_rn: icsfExportRnField
.export_options: icsfExportHostVersionCheck, icsfExportNocvCheck
.import_input: icsfImportInputField
.import_kek: icsfImportKekField
.import_type: icsfImportTypeCombo
.import_cv: icsfImportCvField
.import_variant: icsfImportVariantCombo
.import_mode: icsfImportModeCombo
.inspect_input: icsfInspectInputArea
.resolve_input: icsfResolveInputField
.resolve_kek: icsfResolveKekField
.resolve_expected_key: icsfResolveExpectedKeyField
.resolve_expected_kcv: icsfResolveExpectedKcvField
.resolve_type: icsfResolveTypeCombo
.report: icsfKeyWrapReportArea
# module-help:op_keys_icsf_keywrap end
# module-help:op_cert_gen begin
@op_cert_gen certificates.fxml
generate: help_handleGenerateCertificate -> subject, validity, key_type, sign_algorithm, san, root_ca, output
generate_csr: help_handleGenerateCSR -> subject, key_type, sign_algorithm, san, output
.subject: certCNField, certOrgField, certOUField, certCountryField, certStateField, certLocalityField, certEmailField
.validity: certValidityField
.key_type: certKeyTypeCombo
.sign_algorithm: certSignAlgoCombo
.san: certSanDnsField, certSanIpField
.root_ca: certRootCaCheck
.output: certOutputArea
# module-help:op_cert_gen end
# module-help:op_cert_issue begin
@op_cert_issue certificates.fxml
issue: help_handleIssueCertificateFromCsr -> csr, ca_cert, ca_key, validity, signature, profile, path_length, result
.csr: certIssueCsrArea
.ca_cert: certIssueCaCertArea
.ca_key: certIssueCaKeyArea
.validity: certIssueValidityField
.signature: certIssueSignatureField
.profile: certIssueProfileCombo
.path_length: certIssuePathLengthField
.result: certIssueResultArea
# module-help:op_cert_issue end
# module-help:op_cert_parse begin
@op_cert_parse certificates.fxml
parse: help_handleParseCertificate -> format, input, result
apply_template: help_handleApplyCertTemplate, help_handleResetCertDefaults -> template, format
.template: certTemplateCombo
.format: certFormatCombo
.input: certInputArea
.result: certParseResultArea
# module-help:op_cert_parse end
# module-help:op_cert_compare begin
@op_cert_compare certificates.fxml
compare: help_handleCompareCertificates -> left, right, result
.left: certCompareLeftArea
.right: certCompareRightArea
.result: certCompareResultArea
# module-help:op_cert_compare end
# module-help:op_cert_val begin
@op_cert_val certificates.fxml
validate: help_handleValidateCertificate -> certificate, issuer, result
.certificate: valCertInput
.issuer: valIssuerInput
.result: valResultArea
# module-help:op_cert_val end
# module-help:op_cert_chain begin
@op_cert_chain certificates.fxml
validate_chain: help_handleValidateCertificateChain, help_handleExportChainReport -> chain, crls, result
generate_crl: help_handleGenerateCrl -> crl_issuer_cert, crl_issuer_key, crl_result
revoke: help_handleRevokeCrl -> crl_issuer_cert, crl_issuer_key, existing_crl, serial, reason, crl_result
.chain: chainInputArea
.crls: chainCrlInputArea
.result: chainResultArea
.crl_issuer_cert: crlIssuerCertArea
.crl_issuer_key: crlIssuerKeyArea
.existing_crl: crlExistingCrlArea
.serial: crlRevokeSerialField
.reason: crlRevokeReasonCombo
.crl_result: crlResultArea
# module-help:op_cert_chain end
# module-help:op_cert_cms begin
@op_cert_cms certificates.fxml
sign: help_handleCMSSign -> input, sign_source, signer_cert, signer_key, token_alias, detached, cades, tsa_url, output
verify: help_handleCMSVerify -> input, detached_data, output
add_lt: help_handleUpgradeCadesLt -> input, output
encrypt: help_handleCMSEncrypt -> input, encrypt_source, recipient_cert, output
decrypt: help_handleCMSDecrypt -> input, encrypt_source, recipient_key, encrypt_token_alias, output
load_token_keys: help_handleLoadCMSKeys, help_handleLoadCMSEncryptKeys -> token_alias, encrypt_token_alias
.input: cmsInputArea
.sign_source: cmsSignSourceLocalRadio, cmsSignSourcePkcs11Radio
.signer_cert: cmsSignCertArea
.signer_key: cmsSignKeyArea
.token_alias: cmsSignKeyAliasCombo
.detached_data: cmsVerifyDataArea
.detached: cmsDetachedCheck
.cades: cmsCadesBesCheck, cmsCadesTCheck
.tsa_url: cmsCadesTsaUrlField
.encrypt_source: cmsEncryptSourceLocalRadio, cmsEncryptSourcePkcs11Radio
.recipient_cert: cmsEncryptCertArea
.recipient_key: cmsDecryptKeyArea
.encrypt_token_alias: cmsEncryptKeyAliasCombo
.output: cmsOutputArea
# module-help:op_cert_cms end
# module-help:op_pades begin
@op_pades pades.fxml
sign: help_handleSign -> input, output, key_source, pkcs12, token_alias, timestamp, visible, result
inspect: help_handleInspect -> input, result
validate: help_handleValidate, help_handleSaveValidationReports -> input, truststore, crl_evidence, result
choose_files: help_handleChooseInput, help_handleChooseOutput, help_handleChoosePkcs12, help_handleChooseTrustStore, help_handleChooseCrlEvidence, help_handleLoadTokenKeys -> input, output, pkcs12, truststore, crl_evidence, token_alias
clear_reset: help_handleClear, help_handleReset -> input
.input: padesInputPathField
.output: padesOutputPathField
.key_source: padesSourceLocalRadio, padesSourcePkcs11Radio
.pkcs12: padesPkcs12PathField, padesPasswordField
.token_alias: padesPkcs11AliasCombo
.timestamp: padesTimestampCheck, padesTsaUrlField
.visible: padesVisibleSignatureCheck, padesVisiblePageField, padesVisibleXField, padesVisibleYField, padesVisibleWidthField, padesVisibleHeightField, padesVisibleTextField
.truststore: padesTrustStorePathField, padesTrustStorePasswordField
.crl_evidence: padesCrlEvidenceField
.result: padesResultArea
# module-help:op_pades end
# module-help:op_asic_s begin
@op_asic_s asic.fxml
create: help_handleCreate -> input, output, key_source, pkcs12, token_alias, format, level, tsa_url, revocation, result
create_e: help_handleCreateE, help_handleChooseAsicEPayloads -> payloads, output, key_source, format, level, result
inspect: help_handleInspect -> input, truststore, revocation, online_revocation, result
choose_files: help_handleChooseInput, help_handleChooseOutput, help_handleChoosePkcs12, help_handleChooseRevocationFiles, help_handleChooseTrustStore, help_handleLoadTokenKeys -> input, output, pkcs12, revocation, truststore, token_alias
clear_reset: help_handleClear, help_handleReset -> input
.input: asicInputPathField
.output: asicOutputPathField
.key_source: asicSourceLocalRadio, asicSourcePkcs11Radio
.pkcs12: asicPkcs12PathField, asicPasswordField
.token_alias: asicPkcs11AliasCombo
.format: asicFormatCombo
.level: asicLevelCombo
.tsa_url: asicTsaUrlField
.revocation: asicRevocationFilesField
.online_revocation: asicOnlineRevocationCheck
.payloads: asicEPayloadsField
.truststore: asicTrustStorePathField, asicTrustStorePasswordField
.result: asicResultArea
# module-help:op_asic_s end
# module-help:op_cms_inspector begin
@op_cms_inspector cms_inspector.fxml
inspect: help_handleInspectCms -> input, report
validate: help_handleValidateSignedData -> input, detached, content, truststore_password, report
load: help_handleLoadCmsFile, help_handleLoadContentFile, help_handleSelectTruststore -> input, content, truststore_password
export: help_handleExportReport -> report
clear_reset: help_handleClear, help_handleReset -> input
.input: cmsInputArea
.detached: cmsDetachedCheck
.content: cmsContentArea
.truststore_password: truststorePasswordField
.report: cmsReportArea
# module-help:op_cms_inspector end
# module-help:op_jose_jwt begin
@op_jose_jwt jose.fxml
generate: help_handleGenerateSignedJWT -> algorithm, template, serialization, unencoded, signing_key, secret_format, second_signer, payload, protected_header, output
apply_claims: help_handleApplyJWTClaims -> claims, payload
load_signing_key: help_handleLoadJWTKey, jwtKeyShelfMenu -> signing_key
validate: help_handleValidateJWT -> token, verification_key, validate_secret_format, expected_claims, time_checks, policy, header_trust, profile_checks, decoded, findings
load_verification_key: help_handleLoadJWTValidateKey, jwtValidateKeyShelfMenu -> verification_key
detached: help_handleGenerateDetachedJWS, help_handleVerifyDetachedJWS -> detached_algorithm, detached_serialization, detached_payload, detached_keys, detached_header, detached_token
clear_reset: help_handleClear, help_handleReset -> algorithm
.algorithm: jwtAlgoCombo
.template: jwtTemplateCombo
.serialization: jwsSerializationCombo
.unencoded: jwsUnencodedPayloadCheck
.signing_key: jwtKeyArea
.secret_format: jwtSecretFormatCombo
.second_signer: jwtAlgo2Combo, jwtKeyArea2
.claims: jwtIssField, jwtSubField, jwtAudField, jwtExpField
.payload: jwtPayloadArea
.protected_header: jwtProtectedHeaderArea
.output: jwtOutputArea
.token: jwtValidateTokenArea
.verification_key: jwtValidateKeyArea
.validate_secret_format: jwtValidateSecretFormatCombo
.expected_claims: jwtExpectedIssField, jwtExpectedAudField
.time_checks: jwtCheckExpiryCheck, jwtClockSkewField
.policy: jwtAllowedAlgorithmsField, jwtAcceptNoneCheck, jwtOidcStrictCheck, jwtRfc9068Check
.header_trust: jwtTrustHeaderKeyCheck, jwtTrustAnchorsArea, jwtCertificateDateField, jwtUnderstoodCritField, jwtIgnoreCritCheck
.profile_checks: jwtExpectedTypeField, jwtExpectedContentTypeField, jwtExpectedNonceField, jwtAccessTokenField, jwtAuthorizationCodeField, jwtExpectedJktField, jwtExpectedX5tField
.decoded: jwtDecodedHeaderArea, jwtDecodedPayloadArea
.findings: jwtFindingsArea
.detached_algorithm: detachedAlgoCombo
.detached_serialization: detachedSerializationCombo, detachedUnencodedCheck
.detached_payload: detachedPayloadArea
.detached_keys: detachedSigningKeyArea, detachedVerificationKeyArea, detachedSecretFormatCombo
.detached_header: detachedProtectedHeaderSecretArea
.detached_token: detachedTokenArea
# module-help:op_jose_jwt end
# module-help:op_jose_jwk begin
@op_jose_jwk jose.fxml
pem_to_jwk: pemToJwkBtn -> key_type, curve, kid, use, key_ops, secret_format, input, output
jwk_to_pem: jwkToPemBtn -> input, secret_format, output
thumbprint: help_handleCalculateThumbprint -> input, output
inspect: jwkInspectMetadataBtn -> input, output
jwks: help_handleNewJWKS, help_handleLoadJWKS, help_handleRotateKey, help_handleExportPublicJWKS -> jwks, rotate_algorithm
.key_type: jwkKeyTypeCombo
.curve: jwkCurveCombo
.kid: jwkKeyIdField
.use: jwkUseCombo
.key_ops: jwkKeyOpsField
.secret_format: jwkSecretFormatCombo
.input: jwkInputArea
.output: jwkOutputArea
.jwks: jwksSecretArea
.rotate_algorithm: jwksRotateAlgoCombo
# module-help:op_jose_jwk end
# module-help:op_jose_jwa begin
@op_jose_jwa jose.fxml
browse:  -> table
.table: jwaTable
# module-help:op_jose_jwa end
# module-help:op_jose_insp begin
@op_jose_insp jose.fxml
analyze: help_handleInspectToken -> input
report: help_handleOpenExpandedInspectorReport, help_handleCopyInspectorOutput, help_handleClearInspector -> input
.input: inspectorInputArea
# module-help:op_jose_insp end
# module-help:op_cose_sign1 begin
@op_cose_sign1 cose.fxml
sign: help_handleSign1 -> algorithm, private_key, public_key, payload, output
verify: help_handleVerify1 -> verify_public_key, message, result
clear_reset: help_handleClear, help_handleReset -> algorithm
.algorithm: sign1AlgoCombo
.private_key: sign1PrivateKeyArea
.public_key: sign1PublicKeyArea
.payload: sign1PayloadArea
.output: sign1OutputArea
.verify_public_key: verify1PublicKeyArea
.message: verify1MessageArea
.result: verify1ResultArea
# module-help:op_cose_sign1 end
# module-help:op_cose_mac0 begin
@op_cose_mac0 cose.fxml
create: help_handleMac0, help_handleGenerateMac0Key -> algorithm, key, payload, output
verify: help_handleVerifyMac0 -> verify_key, message, result
clear_reset: help_handleClear, help_handleReset -> algorithm
.algorithm: mac0AlgoCombo
.key: mac0KeyField
.payload: mac0PayloadArea
.output: mac0OutputArea
.verify_key: verifyMac0KeyField
.message: verifyMac0MessageArea
.result: verifyMac0ResultArea
# module-help:op_cose_mac0 end
# module-help:op_cose_encrypt0 begin
@op_cose_encrypt0 cose.fxml
encrypt: help_handleEncrypt0, help_handleGenerateEncrypt0Key -> algorithm, key, payload, output
decrypt: help_handleDecrypt0 -> decrypt_key, message, result
clear_reset: help_handleClear, help_handleReset -> algorithm
.algorithm: encrypt0AlgoCombo
.key: encrypt0KeyField
.payload: encrypt0PayloadArea
.output: encrypt0OutputArea
.decrypt_key: decrypt0KeyField
.message: decrypt0MessageArea
.result: decrypt0ResultArea
# module-help:op_cose_encrypt0 end
# module-help:op_asn1_dec begin
@op_asn1_dec asn1.fxml
parse: help_handleParseASN1 -> input_format, example, input, strict_der, tree, details
export: help_handleExportASN1Tree, help_handleExportASN1Json, help_handleExportASN1Markdown -> tree
canonicalize: help_handleCanonicalizeASN1Der -> input, strict_der
load_oids: help_handleLoadASN1OidRegistry -> tree
clear_reset: help_handleClearASN1, help_resetModule -> input
.input_format: asn1InputFormatCombo
.example: asn1TypeCombo
.input: asn1InputArea
.strict_der: asn1StrictDerCheck
.tree: asn1TreeView
.details: asn1DetailsArea
# module-help:op_asn1_dec end
# module-help:op_asn1_enc begin
@op_asn1_enc asn1.fxml
encode: help_handleEncodeASN1 -> type, value_format, value, output
copy: help_handleCopyASN1Output -> output
.type: asn1EncodeTypeCombo
.value_format: asn1EncodeInputFormatCombo
.value: asn1EncodeInputArea
.output: asn1EncodeOutputArea
# module-help:op_asn1_enc end
# module-help:op_pqc_gen begin
@op_pqc_gen pqc.fxml
generate: pqcGenerateKeyBtn -> algorithm, public_key, private_key, details
import_export: help_handleImportPQCKeys, help_handleExportPQCPublicKey, help_handleExportPQCPrivateKey, pqcKeyShelfMenu -> public_key, private_key
kem: help_handlePQCEncapsulate, help_handlePQCDecapsulate -> kem_algorithm, kem_ciphertext, kem_secrets
benchmark: pqcBenchmarkBtn -> benchmark_algorithm, benchmark
.algorithm: pqcAlgorithmCombo
.public_key: pqcPublicKeyArea
.private_key: pqcPrivateKeyArea
.details: pqcKeyDetailsArea
.kem_algorithm: pqcKemAlgoCombo
.kem_ciphertext: pqcKemCiphertextArea
.kem_secrets: pqcKemSharedSecretField, pqcAliceSecretField
.benchmark_algorithm: pqcBenchmarkAlgoCombo
.benchmark: pqcBenchmarkArea
# module-help:op_pqc_gen end
# module-help:op_pqc_sign begin
@op_pqc_sign pqc.fxml
sign: help_handlePQCSign -> algorithm, input, signature
verify: help_handlePQCVerify, help_handlePastePqcVerifySignature, pqcVerifyShelfMenu -> algorithm, input, verify_signature
.algorithm: pqcSignAlgoCombo
.input: pqcSignInputArea
.signature: pqcSignOutputArea
.verify_signature: pqcVerifySignatureField
# module-help:op_pqc_sign end
# module-help:op_xml_sign begin
@op_xml_sign xml_security.fxml
sign: help_handleSignXML, help_handleSaveSignedXML -> input, source, keystore, alias, level, packaging, tsa, output
load_keys: help_handleLoadXMLKeys, help_handleBrowseXMLKey, help_handleBrowseXMLSignInput -> input, keystore, alias
tsa: help_handleSaveTSA, help_handleTestTSA, help_handleLoadTSASavedProfile, help_handleDeleteTSASavedProfile, help_handleSaveTSASavedProfile -> tsa, tsa_auth, tsa_profile
clear_reset: help_handleClear, help_handleReset -> input
.input: xmlSignInputPathField
.source: xmlSignSourceLocalRadio, xmlSignSourcePkcs11Radio
.keystore: xmlSignKeyPathField, xmlSignKeyPasswordField
.alias: xmlSignKeyAliasCombo
.level: xmlSignLevelCombo
.packaging: xmlSignPackagingCombo
.tsa: xmlSignTsaUrlText
.tsa_auth: xmlSignTsaAuthTypeCombo, xmlSignTsaUserField, xmlSignTsaPasswordField
.tsa_profile: xmlSignTsaProfileCombo, xmlSignTsaProfileNameField
.output: xmlSignOutputArea
# module-help:op_xml_sign end
# module-help:op_xml_inspect begin
@op_xml_inspect xml_security.fxml
inspect: help_handleInspectSignedXML, help_handleBrowseXMLInspectorInput -> input, report
.input: xmlInspectInputArea
.report: xmlInspectReportArea
# module-help:op_xml_inspect end
# module-help:op_xml_tsa begin
@op_xml_tsa xml_security.fxml
request: help_handleRequestTimestamp, help_handleSaveTimestampToken, help_handleBrowseTimestampFile -> file, hash, url, report
inspect: help_handleInspectTimestampToken, help_handleBrowseTimestampToken -> token, report
validate: help_handleValidateTimestampToken, help_handleBrowseTimestampTrustStore -> token, file, truststore, report
.file: xmlTimestampFileField
.hash: xmlTimestampHashCombo
.url: xmlTimestampUrlField
.token: xmlTimestampTokenField
.truststore: xmlTimestampTrustStoreField, xmlTimestampTrustStorePasswordField
.report: xmlTimestampReportArea
# module-help:op_xml_tsa end
# module-help:op_xml_verify begin
@op_xml_verify xml_security.fxml
verify: help_handleVerifyXML -> input, truststore_profile, truststore, report
truststore: help_handleLoadXMLTrustStoreProfile, help_handleBrowseXMLTrustStore -> truststore_profile, truststore
.input: xmlVerifyInputArea
.truststore_profile: xmlVerifyTrustStoreProfileCombo
.truststore: xmlVerifyTrustStorePathField, xmlVerifyTrustStorePasswordField
.report: xmlVerifyReportArea
# module-help:op_xml_verify end
# module-help:op_wss_sign begin
@op_wss_sign wss_security.fxml
sign: help_handleSignWSS, help_handleSaveSignedWss -> input, algorithm, timestamp, keystore, alias, key_password, output
load: help_handleBrowseWssSignInput, help_handleBrowseWssKey, help_handleLoadWssKeys -> input, keystore, alias
clear_reset: help_handleClear, help_handleReset -> input
.input: wssSignInputArea
.algorithm: wssSignAlgorithmCombo
.timestamp: wssIncludeTimestampCheck, wssTimestampValiditySpinner, wssSignTimestampCheck
.keystore: wssSignKeyPathField, wssSignKeyPasswordField
.alias: wssSignKeyAliasCombo
.key_password: wssSignPrivateKeyPasswordField
.output: wssSignOutputArea
# module-help:op_wss_sign end
# module-help:op_wss_verify begin
@op_wss_verify wss_security.fxml
verify: help_handleVerifyWSS, help_handleBrowseWssVerifyInput, help_handleBrowseWssTrustStore -> input, trusted_certificate, report
.input: wssVerifyInputArea
.trusted_certificate: wssVerifyTrustStorePathField
.report: wssVerifyReportArea
# module-help:op_wss_verify end
# module-help:op_wss_username_add begin
@op_wss_username_add wss_security.fxml
add: help_handleAddUsernameToken, help_handleBrowseUsernameCreateInput, help_handleSaveUsernameTokenXml -> input, username, password, password_type, output
.input: wssUsernameCreateInputArea
.username: wssUsernameCreateNameField
.password: wssUsernameCreatePasswordField
.password_type: wssUsernamePasswordTypeCombo
.output: wssUsernameCreateOutputArea
# module-help:op_wss_username_add end
# module-help:op_wss_username_verify begin
@op_wss_username_verify wss_security.fxml
verify: help_handleVerifyUsernameToken, help_handleBrowseUsernameVerifyInput -> input, expected_username, expected_password, max_age, report
.input: wssUsernameVerifyInputArea
.expected_username: wssUsernameExpectedNameField
.expected_password: wssUsernameExpectedPasswordField
.max_age: wssUsernameMaxAgeSpinner
.report: wssUsernameVerifyReportArea
# module-help:op_wss_username_verify end
# module-help:op_wss_encrypt begin
@op_wss_encrypt wss_security.fxml
encrypt: help_handleEncryptSoapBody, help_handleBrowseWssEncryptInput, help_handleBrowseWssEncryptCertificate, help_handleSaveEncryptedSoap -> input, certificate, data_algorithm, key_transport, output, report
.input: wssEncryptInputArea
.certificate: wssEncryptCertificatePathField
.data_algorithm: wssEncryptDataAlgorithmCombo
.key_transport: wssEncryptKeyTransportCombo
.output: wssEncryptOutputArea
.report: wssEncryptReportArea
# module-help:op_wss_encrypt end
# module-help:op_wss_decrypt begin
@op_wss_decrypt wss_security.fxml
decrypt: help_handleDecryptSoapBody, help_handleBrowseWssDecryptInput, help_handleBrowseWssDecryptKeyStore, help_handleSaveDecryptedSoap -> input, keystore, key_password, output, report
.input: wssDecryptInputArea
.keystore: wssDecryptKeyStorePathField, wssDecryptKeyStorePasswordField
.key_password: wssDecryptPrivateKeyPasswordField
.output: wssDecryptOutputArea
.report: wssDecryptReportArea
# module-help:op_wss_decrypt end
# module-help:op_pay_cvv begin
@op_pay_cvv payments.fxml
generate: help_handleGenerateCvv -> type, cvk, pan, expiry, service_code, atc, result
verify: help_handleVerifyCvv -> type, cvk, pan, expiry, service_code, atc, result
.type: cvvTypeCombo
.cvk: cvkAField, cvkBField
.pan: panFieldCvv
.expiry: expiryDateField
.service_code: serviceCodeField
.atc: atcField
.result: cvvResultArea
# module-help:op_pay_cvv end
# module-help:op_pay_pin_gen begin
@op_pay_pin_gen payments.fxml
generate_pin: help_handleGenerateIbm3624Pin -> pvk, decimalization, offset, pan, validation_data, ibm_result
verify_pin: help_handleVerifyIbm3624Pin -> pvk, decimalization, offset, pan, pin_to_verify, ibm_result
generate_offset: help_handleGenerateOffsetUtility -> offset_inputs, offset_result
generate_pvv: help_handleGeneratePVVUtility -> pvv_inputs, pvv_result
find_pin: help_handleDerivePinFromPvvUtility -> find_inputs, find_result
.pvk: ibm3624PvkField
.decimalization: ibm3624ConvTableField
.offset: ibm3624OffsetField
.pan: ibm3624PanField
.validation_data: ibm3624StartField, ibm3624LengthField, ibm3624PadField
.pin_to_verify: ibm3624PinVerifyField
.ibm_result: ibm3624ResultArea
.offset_inputs: genOffsetPvkField, genOffsetDecTableField, genOffsetPanField, genOffsetPinField, genOffsetStartField, genOffsetLengthField, genOffsetPadField
.offset_result: genOffsetResultArea
.pvv_inputs: genPvvPvkField, genPvvPanField, genPvvPinField, genPvvKeyIndexField
.pvv_result: genPvvResultArea
.find_inputs: derivePvvPvkField, derivePvvPanField, derivePvvTargetPvvField, derivePvvKeyIndexField
.find_result: derivePvvResultArea
# module-help:op_pay_pin_gen end
# module-help:op_pay_dukpt begin
@op_pay_dukpt payments.fxml
inspect: help_handleInspectDukpt -> scheme, bdk, ksn, usage, result
pin_block: help_handleAesDukptPinBlock -> bdk, ksn, aes_pin_block, result
.scheme: dukptSchemeCombo
.bdk: dukptBdkField
.ksn: dukptKsnField
.usage: dukptTdesUsageCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo
.aes_pin_block: dukptAesPinBlockField, dukptAesPinOperationCombo
.result: dukptResultArea
# module-help:op_pay_dukpt end
# module-help:op_pay_payshield_host_bank begin
@op_pay_payshield_host_bank payments.fxml
compose: help_handleHsmHostCompose -> header, command_code, body, trailer, tcp_prefix, result
analyze: help_handleHsmHostAnalyzeCommand, help_handleHsmHostAnalyzeResponse -> captured_frame, header_length, tcp_prefix, result
example: help_handleHsmHostLoadExample -> command_code
.header: hsmHostHeaderField
.command_code: hsmHostCommandCodeField
.body: hsmHostBodyField
.trailer: hsmHostTrailerField
.header_length: hsmHostHeaderLengthField
.tcp_prefix: hsmHostTcpPrefixCheck
.captured_frame: hsmHostCapturedFrameArea
.result: hsmHostResultArea
# module-help:op_pay_payshield_host_bank end
# module-help:op_pay_iso8583 begin
@op_pay_iso8583 payments.fxml
parse: help_handleParseIso8583 -> profile, bitmap_encoding, length_encoding, message, report
build: help_handleBuildIso8583 -> profile, bitmap_encoding, length_encoding, message, report
.profile: iso8583ProfileCombo
.bitmap_encoding: iso8583BitmapEncodingCombo
.length_encoding: iso8583LengthEncodingCombo
.message: iso8583MessageArea
.report: iso8583ReportArea
# module-help:op_pay_iso8583 end
# module-help:op_pay_emv_tlv begin
@op_pay_emv_tlv emv.fxml
inspect: help_handleInspectTlv -> tlv, tlv_result
build_dol: help_handleBuildDol -> dol_template, dol_values, dol_result
.tlv: emvTlvInputArea
.tlv_result: emvTlvResultArea
.dol_template: emvDolTemplateField
.dol_values: emvDolValuesArea
.dol_result: emvDolResultArea
# module-help:op_pay_emv_tlv end
# module-help:op_pay_emv_ops begin
@op_pay_emv_ops emv.fxml
derive_keys: help_handleDeriveSessionKey -> imk, card, icc_method, atc, session_result
arqc: help_handleGenerateARQC -> arqc_session_key, terminal_data, transaction_fields, arqc_padding, arqc_result
arpc: help_handleGenerateARPC, help_handleVerifyARQC -> arpc_session_key, arqc, arc, arpc_options, arpc_result
secure_messaging: help_handleSmDeriveSessionKeys, help_handleSmEncipherPin, help_handleSmGenerateMac, help_handleSmLoadExample -> sm_scheme, sm_keys, sm_pin, sm_command, sm_result
visa_hce: help_handleHceLuk, help_handleHceMsd, help_handleHceQvsdc, help_handleHceLoadExample -> hce_luk, hce_msd, hce_qvsdc, hce_result
data_storage: help_handleDsDspk, help_handleDsDigest, help_handleDsSummary, help_handleDsLoadExample -> ds_inputs, ds_result
dynamic_number: help_handleIccDnCalculate, help_handleIccDnLoadExample -> dn_inputs, dn_result
cap_token: help_handleCapCalculate, help_handleCapLoadExample -> cap_inputs, cap_result
track2: help_handleEncodeTrack2, help_handleDecodeTrack2 -> track2_encode, track2_decode, track2_result
clear_reset: help_handleClear, help_handleReset -> imk
.imk: imkField
.card: panFieldSession, panSeqFieldSession
.icc_method: iccMethodCombo
.atc: emvAtcField
.session_result: sessionKeyResultArea
.arqc_session_key: skARQCField
.terminal_data: arqcTerminalDataField
.transaction_fields: amountField, amountOtherField, currencyField, countryField, atcARQCField, tvrField, txDateField, txTypeField, unField, iccDataField
.arqc_padding: arqcPaddingMethodCombo
.arqc_result: arqcResultArea
.arpc_session_key: skARPCField
.arqc: arqcField
.arc: arcField
.arpc_options: csuField, propAuthDataField, arpcMethodCombo
.arpc_result: arpcResultArea
.sm_scheme: smSchemeCombo
.sm_keys: smMkSmiField, smMkSmcField, smPanSeqField, smUdkSmiField, smUdkSmcField, smAcField, smCommandNumberField, smAtcField, smSkMacField, smSkEncField
.sm_pin: smPinField, smUdkAField
.sm_command: smHeaderField, smDataField
.sm_result: smResultArea
.hce_luk: hceUdkField, hceYearField, hceHoursField, hceCounterField
.hce_msd: hceMsdLukField, hceMsdAtcField, hceDeviceTypeField
.hce_qvsdc: hceQvsdcLukField, hceAmountField, hceOtherAmountField, hceCountryField, hceTvrField, hceCurrencyField, hceDateField, hceTypeField, hceUnField, hceAipField, hceQvsdcAtcField, hceCvrField
.hce_result: hceResultArea
.ds_inputs: dsIdField, dsOperatorIdField, dsInputField, dsSummary1Field, dsAmountField, dsCurrencyField, dsRcpField, dsGacField, dsDsUnField, dsUnField
.ds_result: dsResultArea
.dn_inputs: dnMkField, dnPanField, dnAtcField, dnUnField
.dn_result: dnResultArea
.cap_inputs: capIpbField, capIafField, capPanSnField, capCidField, capAtcField, capAcField, capIadField
.cap_result: capResultArea
.track2_encode: panTrack2Field, expiryTrack2Field, serviceCodeFieldTrack2, track2DiscretionaryInputField
.track2_decode: track2InputField
.track2_result: track2ResultArea
# module-help:op_pay_emv_ops end
# module-help:op_pay_emv_oda begin
@op_pay_emv_oda emv.fxml
recover: help_handleOdaRecoverKeys -> ca_key, issuer_certificate, icc_certificate, static_data, pan, result
verify_sda: help_handleOdaVerifySda -> ca_key, issuer_certificate, static_data, ssad, result
verify_dda: help_handleOdaVerifyDda -> ca_key, issuer_certificate, icc_certificate, sdad, terminal_data, result
verify_cda: help_handleOdaVerifyCda -> ca_key, issuer_certificate, icc_certificate, sdad, terminal_data, cda_data, result
test_card: help_handleOdaIssueTestCard, help_handleOdaClear -> ca_key
.ca_key: odaCaModulusArea, odaCaExponentField
.issuer_certificate: odaIssuerCertificateArea, odaIssuerRemainderField, odaIssuerExponentField
.icc_certificate: odaIccCertificateArea, odaIccRemainderField, odaIccExponentField
.static_data: odaStaticDataArea
.pan: odaPanField
.ssad: odaSsadArea
.sdad: odaSdadArea
.terminal_data: odaTerminalDataField
.cda_data: odaCidField, odaTransactionDataArea
.result: odaResultArea
# module-help:op_pay_emv_oda end
# module-help:op_wallet_sdjwt begin
@op_wallet_sdjwt wallet.fxml
issue: help_handleSdJwtIssue -> algorithm, issuer_key, claims, disclosable, vct, decoys, issue_output
present: help_handleSdJwtPresent -> present_input, reveal, key_binding, holder_key, present_output
verify: help_handleSdJwtVerify -> verify_input, verify_issuer_key, verify_holder_key, verify_expected, verify_output
inspect: help_handleSdJwtInspect -> inspect_input, inspect_output
clear_example: help_handleClear, help_handleLoadExample -> claims
.algorithm: sdJwtAlgoCombo
.issuer_key: sdJwtIssuerKeyArea
.claims: sdJwtClaimsArea
.disclosable: sdJwtDisclosableArea
.vct: sdJwtVctField
.decoys: sdJwtDecoyField
.issue_output: sdJwtIssueOutputArea
.present_input: sdJwtPresentInputArea
.reveal: sdJwtRevealArea
.key_binding: sdJwtAudienceField, sdJwtNonceField
.holder_key: sdJwtHolderKeyArea
.present_output: sdJwtPresentOutputArea
.verify_input: sdJwtVerifyInputArea
.verify_issuer_key: sdJwtVerifyIssuerKeyArea
.verify_holder_key: sdJwtVerifyHolderKeyArea
.verify_expected: sdJwtVerifyAudienceField, sdJwtVerifyNonceField
.verify_output: sdJwtVerifyOutputArea
.inspect_input: sdJwtInspectInputArea
.inspect_output: sdJwtInspectOutputArea
# module-help:op_wallet_sdjwt end
# module-help:op_wallet_mdoc begin
@op_wallet_mdoc wallet.fxml
issue: help_handleMdocIssue -> doc_type, digest, issuer_key, signer_cert, device_key, elements, validity, issue_output
verify: help_handleMdocVerify, help_handleMdocInspect -> verify_input, verify_issuer_key, verify_output
clear_example: help_handleClear, help_handleLoadExample -> elements
.doc_type: mdocDocTypeField
.digest: mdocDigestCombo
.issuer_key: mdocIssuerKeyArea
.signer_cert: mdocSignerCertArea
.device_key: mdocDeviceKeyArea
.elements: mdocClaimsArea
.validity: mdocValidityField
.issue_output: mdocIssueOutputArea
.verify_input: mdocVerifyInputArea
.verify_issuer_key: mdocVerifyIssuerKeyArea
.verify_output: mdocVerifyOutputArea
# module-help:op_wallet_mdoc end
# module-help:op_wallet_status_list begin
@op_wallet_status_list wallet.fxml
issue: help_handleStatusListIssue -> bits, statuses, uri, algorithm, key, output
resolve: help_handleStatusListResolve, help_handleStatusListDescribe -> token, index, verify_key, resolve_output
clear_example: help_handleClear, help_handleLoadExample -> statuses
.bits: statusListBitsCombo
.statuses: statusListStatusesArea
.uri: statusListUriField
.algorithm: statusListAlgoCombo
.key: statusListKeyArea
.output: statusListOutputArea
.token: statusListTokenArea
.index: statusListIndexField
.verify_key: statusListVerifyKeyArea
.resolve_output: statusListResolveOutputArea
# module-help:op_wallet_status_list end
# module-help:op_wallet_eidas_cert begin
@op_wallet_eidas_cert wallet.fxml
inspect: help_handleEidasCertInspect -> certificate, output
clear_example: help_handleClear, help_handleLoadExample -> certificate
.certificate: eidasCertArea
.output: eidasCertOutputArea
# module-help:op_wallet_eidas_cert end
# module-help:op_wallet_trusted_list begin
@op_wallet_trusted_list wallet.fxml
inspect: help_handleTrustedListInspect -> xml, output
verify: help_handleTrustedListVerify -> xml, output
find: help_handleTrustedListFind -> xml, lookup_cert, output
clear_example: help_handleClear, help_handleLoadExample -> xml
.xml: trustedListXmlArea
.lookup_cert: trustedListCertArea
.output: trustedListOutputArea
# module-help:op_wallet_trusted_list end
# module-help:op_wallet_trusted_entity_list_json begin
@op_wallet_trusted_entity_list_json wallet.fxml
inspect: help_handleTrustedEntityListJsonInspect -> json, signer_cert, search_cert, output
clear_example: help_handleClear, help_handleLoadExample -> json
.json: trustedEntityListJsonArea
.signer_cert: trustedEntityListSignerCertArea
.search_cert: trustedEntityListSearchCertArea
.output: trustedListOutputArea
# module-help:op_wallet_trusted_entity_list_json end
# module-help:op_wallet_cbor begin
@op_wallet_cbor wallet.fxml
inspect: help_handleCborInspect, help_handleCborToJson -> input, view, output
from_json: help_handleCborFromJson -> json, from_json_output
clear_example: help_handleClear, help_handleLoadExample -> input
.input: cborInputArea
.view: cborViewCombo
.output: cborOutputArea
.json: cborJsonArea
.from_json_output: cborFromJsonOutputArea
# module-help:op_wallet_cbor end
# module-help:op_wallet_sca begin
@op_wallet_sca wallet.fxml
build: help_handleScaBuild -> type, credential_ids, payload, entry
verify: help_handleScaVerify -> presentation, transaction_data, issuer_key, holder_key, expected, output
inspect_request: help_handleOid4vpInspect -> request, request_key, request_output
clear_example: help_handleClear, help_handleLoadExample -> payload
.type: scaTypeCombo
.credential_ids: scaCredentialIdsField
.payload: scaPayloadArea
.entry: scaEntryArea
.presentation: scaPresentationArea
.transaction_data: scaTransactionDataArea
.issuer_key: scaIssuerKeyArea
.holder_key: scaHolderKeyArea
.expected: scaAudienceField, scaNonceField, scaResponseModeField
.output: scaOutputArea
.request: oid4vpRequestArea
.request_key: oid4vpKeyArea
.request_output: oid4vpOutputArea
# module-help:op_wallet_sca end
# module-help:op_wallet_ades begin
@op_wallet_ades wallet.fxml
validate: help_handleAdesValidate -> file_name, document, output
etsi_report: help_handleAdesEtsiReport -> file_name, document, output
clear_example: help_handleClear, help_handleLoadExample -> document
.file_name: adesFileNameField
.document: adesDocumentArea
.output: adesOutputArea
# module-help:op_wallet_ades end
# module-help:op_hist_recent begin
@op_hist_recent history.fxml
find: help_handleClearHistoryFilter -> filter, module, visibility, table, details
compare: compareBtn -> table
use_value: openHistoryDetailBtn, copyHistoryDetailBtn, addHistoryToShelfBtn -> details
clear: help_handleClearHistory -> table
.visibility: visibilityCombo
.filter: historyFilterField
.module: historyModuleFilterCombo
.table: historyTable
.details: detailsTable
# module-help:op_hist_recent end
# module-help:op_hist_export begin
@op_hist_export history.fxml
export_record: exportJsonRecordBtn, exportVisibleJsonBtn -> table, visibility
report: exportReportBtn, copyReportBtn -> table
recipe: exportRecipeBtn, help_importOperationRecipe -> table
.table: historyTable
.visibility: visibilityCombo
# module-help:op_hist_export end
# module-help:op_hist_saved begin
@op_hist_saved history.fxml
load:  -> sessions, password
delete:  -> sessions
remove_plaintext:  -> sessions
.sessions: 
.password: 
# module-help:op_hist_saved end
