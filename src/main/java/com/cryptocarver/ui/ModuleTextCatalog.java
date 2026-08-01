package com.cryptocarver.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** English source-text to bundle-key maps for the UX-15A module slice. */
public final class ModuleTextCatalog {
    private ModuleTextCatalog() { }

    private static Map<String, String> common() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Apply Template:", "module.common.applyTemplate");
        map.put("Select a template...", "module.common.selectTemplate");
        map.put("Apply", "module.common.apply");
        map.put("Manage Template", "module.common.manageTemplate");
        map.put("Save Current as Personal Template...", "module.common.saveTemplate");
        map.put("Export Selected Template...", "module.common.exportTemplate");
        map.put("Import Template File...", "module.common.importTemplate");
        map.put("Delete Selected Personal Template", "module.common.deleteTemplate");
        map.put("Reset Defaults", "module.common.resetDefaults");
        map.put("Algorithm:", "module.common.algorithm");
        map.put("Algorithm", "module.common.algorithm");
        map.put("Mode:", "module.common.mode");
        map.put("Padding:", "module.common.padding");
        map.put("Generate", "module.common.generate");
        map.put("Encrypt", "module.common.encrypt");
        map.put("Decrypt", "module.common.decrypt");
        map.put("Inspect", "module.common.inspect");
        map.put("Save to Lab", "module.common.saveToLab");
        map.put("Paste Key", "module.common.pasteKey");
        map.put("Load Key…", "module.common.loadKey");
        map.put("Use from Shelf", "module.common.useShelf");
        map.put("Input:", "module.common.input");
        map.put("Output:", "module.common.output");
        map.put("Cancel", "module.common.cancel");
        map.put("Ready", "module.common.ready");
        return map;
    }

    public static Map<String, String> cipher() {
        Map<String, String> map = common();
        map.put("Data Encryption & Decryption", "module.cipher.title");
        map.put("🔒 Symmetric Cipher (AES, DES, etc.)", "module.cipher.symmetric");
        map.put("Algorithm & Mode", "module.cipher.algorithmMode");
        map.put("Key Source", "module.cipher.keySource");
        map.put("Key Source *:", "module.cipher.keySourceRequired");
        map.put("IV / Nonce & AEAD parameters", "module.cipher.ivAead");
        map.put("AEAD Auth Tag:", "module.cipher.aeadTag");
        map.put("AAD (Optional):", "module.cipher.aad");
        map.put("File Cipher (Streaming)", "module.cipher.fileStreaming");
        map.put("OpenPGP (GPG compatible)", "module.cipher.openPgp");
        map.put("Asymmetric Cipher (RSA)", "module.cipher.asymmetric");
        map.put("Input data", "module.common.inputData");
        map.put("Output result", "module.common.outputResult");
        return map;
    }

    public static Map<String, String> authentication() {
        Map<String, String> map = common();
        map.put("1. Data to authenticate", "module.auth.dataTitle");
        map.put("Enter the message used to generate or verify the signature/MAC. Format is selected in the global 'Payload format' bar above.", "module.auth.dataHelp");
        map.put("🔏 Digital Signatures", "module.auth.signatures");
        map.put("Keys (PEM format):", "module.auth.keysPem");
        map.put("Private Key", "module.auth.privateKey");
        map.put("Public Key", "module.auth.publicKey");
        map.put("Paste Private Key from Clipboard", "module.auth.pastePrivate");
        map.put("Paste Public Key from Clipboard", "module.auth.pastePublic");
        map.put("Load Private Key…", "module.auth.loadPrivate");
        map.put("Load Public Key…", "module.auth.loadPublic");
        map.put("Ready to sign/verify", "module.auth.ready");
        map.put("Sign", "module.auth.sign");
        map.put("Verify Signature", "module.auth.verifySignature");
        map.put("🔐 Message Authentication Codes (MAC)", "module.auth.mac");
        map.put("2. MAC configuration", "module.auth.macConfig");
        map.put("Output length", "module.auth.outputLength");
        map.put("Key material", "module.auth.keyMaterial");
        map.put("Save key to Lab", "module.auth.saveKey");
        map.put("Paste MAC Key", "module.auth.pasteMac");
        map.put("Load MAC Key…", "module.auth.loadMac");
        map.put("Generate MAC", "module.auth.generateMac");
        map.put("Verify an existing MAC", "module.auth.verifyMac");
        map.put("Verify", "module.auth.verify");
        map.put("3. Result", "module.auth.result");
        return map;
    }

    public static Map<String, String> keys() {
        Map<String, String> map = common();
        map.put("🎛 Key Lab", "module.keys.keyLab");
        map.put("Search:", "module.common.search");
        map.put("Import Metadata", "module.keys.importMetadata");
        map.put("Export Metadata", "module.keys.exportMetadata");
        map.put("No keys in Lab. Generate or import one below!", "module.keys.empty");
        map.put("Add New Key", "module.keys.add");
        map.put("Name:", "module.common.name");
        map.put("Key Size:", "module.keys.keySize");
        map.put("Generate Key", "module.keys.generate");
        map.put("Import Key", "module.keys.import");
        map.put("Key Details", "module.keys.details");
        map.put("ID/Alias:", "module.keys.idAlias");
        map.put("Bits:", "module.keys.bits");
        map.put("KCV:", "module.keys.kcv");
        map.put("Fingerprint:", "module.keys.fingerprint");
        map.put("Origin:", "module.keys.origin");
        map.put("Created:", "module.keys.created");
        map.put("Modified:", "module.keys.modified");
        map.put("Status:", "module.keys.status");
        map.put("Key Value:", "module.keys.value");
        map.put("Reveal Value", "module.keys.reveal");
        map.put("Copy ID", "module.keys.copyId");
        map.put("Save Metadata", "module.keys.saveMetadata");
        map.put("Archive", "module.keys.archive");
        map.put("Delete Key", "module.keys.delete");
        map.put("🔑 Key Generation", "module.keys.generation");
        map.put("Type:", "module.keys.type");
        map.put("Generated Key Summary", "module.keys.summary");
        map.put("Copy Key", "module.keys.copyKey");
        map.put("Copy KCV", "module.keys.copyKcv");
        map.put("Copy Summary", "module.keys.copySummary");
        map.put("Validate & Calculate KCVs", "module.keys.validateKcv");
        map.put("🔎 Key Material Inspector", "module.keys.materialInspector");
        map.put("Inspect Material", "module.keys.inspectMaterial");
        map.put("🗄 KeyStore Inspector", "module.keys.keystoreInspector");
        map.put("Load", "module.common.load");
        map.put("Browse…", "module.common.browse");
        map.put("Save Profile", "module.common.saveProfile");
        map.put("Inspect KeyStore", "module.keys.inspectKeystore");
        map.put("🔐 PKCS#11 Token", "module.keys.pkcs11");
        map.put("Connect & Inspect", "module.keys.connectInspect");
        map.put("Disconnect", "module.keys.disconnect");
        map.put("Sign Data", "module.keys.signData");
        map.put("Verify Signature", "module.auth.verifySignature");
        map.put("🔗 Compare Public / Private Key", "module.keys.compare");
        map.put("Compare Pair", "module.keys.comparePair");
        map.put("🔀 Key Sharing (XOR Split/Combine)", "module.keys.sharing");
        map.put("Split Key", "module.keys.split");
        map.put("Combine Components", "module.keys.combine");
        map.put("🔐 Key Derivation (KDF)", "module.keys.kdf");
        map.put("Derive Key", "module.keys.derive");
        map.put("🔗 AES Key Wrap (RFC 3394 / RFC 5649)", "module.keys.keyWrap");
        map.put("Execute Key Wrap", "module.keys.executeWrap");
        map.put("🔒 TR-31 Key Blocks", "module.keys.tr31");
        return map;
    }

    public static Map<String, String> certificates() {
        Map<String, String> map = common();
        map.put("📜 Generate Certificate", "module.cert.generate");
        map.put("Organization:", "module.cert.organization");
        map.put("Org. Unit:", "module.cert.orgUnit");
        map.put("Country:", "module.cert.country");
        map.put("State:", "module.cert.state");
        map.put("Locality:", "module.cert.locality");
        map.put("Email:", "module.common.email");
        map.put("Validity (days):", "module.cert.validity");
        map.put("Key Type:", "module.cert.keyType");
        map.put("Sign Algorithm:", "module.cert.signAlgorithm");
        map.put("SAN DNS:", "module.cert.sanDns");
        map.put("SAN IP:", "module.cert.sanIp");
        map.put("Generate Certificate", "module.cert.generateCertificate");
        map.put("Generate CSR", "module.cert.generateCsr");
        map.put("🏛 Issue Certificate from CSR (Laboratory CA)", "module.cert.issue");
        map.put("Issue Laboratory Certificate", "module.cert.issueButton");
        map.put("🔍 Parse Certificate", "module.cert.parse");
        map.put("Paste PEM Certificate:", "module.cert.pastePem");
        map.put("Parse Certificate", "module.cert.parseButton");
        map.put("⚖ Compare Certificates", "module.cert.compare");
        map.put("Compare Certificates", "module.cert.compareButton");
        map.put("🛡 Validate Certificate", "module.cert.validate");
        map.put("Validate Certificate", "module.cert.validateButton");
        map.put("Validation Report", "module.cert.validationReport");
        map.put("🔗 Validate Chain", "module.cert.validateChain");
        map.put("Validate Chain", "module.cert.validateChainButton");
        map.put("🚫 CRL Management", "module.cert.crl");
        map.put("Generate Empty CRL", "module.cert.generateCrl");
        map.put("Revoke & Update CRL", "module.cert.updateCrl");
        map.put("📄 PAdES PDF Signatures", "module.cert.pades");
        map.put("📦 ASiC-S Containers", "module.cert.asic");
        map.put("📦 CMS Operations", "module.cert.cms");
        map.put("Load Token Keys", "module.cert.loadTokenKeys");
        map.put("Output / Result:", "module.cert.outputResult");
        return map;
    }

    public static Map<String, String> generic() {
        Map<String, String> map = common();
        map.put("🛠 Key & Certificate Format Workbench", "module.generic.workbench");
        map.put("🔐 Hashing", "module.generic.hashing");
        map.put("Calculate Hash", "module.generic.calculateHash");
        map.put("📦 Batch Runner", "module.generic.batch");
        map.put("Run Batch", "module.generic.runBatch");
        map.put("Dry Run", "module.generic.dryRun");
        map.put("Load CSV / JSON Lines…", "module.generic.loadBatch");
        map.put("Export Results…", "module.generic.exportResults");
        map.put("🔄 File Conversion", "module.generic.fileConversion");
        map.put("Convert File", "module.generic.convertFile");
        map.put("Compare Files (streaming)", "module.generic.compareFiles");
        map.put("Preview First 4 KiB", "module.generic.preview");
        map.put("🔤 Manual Conversion", "module.generic.manualConversion");
        map.put("Convert Data", "module.generic.convertData");
        map.put("Compress", "module.generic.compress");
        map.put("Decompress", "module.generic.decompress");
        map.put("Check Digit:", "module.generic.checkDigit");
        map.put("Validation Result:", "module.generic.validationResult");
        return map;
    }
}
