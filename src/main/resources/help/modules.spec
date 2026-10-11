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
