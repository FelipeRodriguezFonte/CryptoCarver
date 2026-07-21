package com.cryptocarver.crypto;

import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.util.DataConverter;

public class PaymentProfileVerifier {

    public static VerificationResult verify(PaymentProfile p) {
        try {
            switch (p.getType()) {
                case DUKPT_TDES:
                    verifyDukptTdes(p);
                    break;
                case DUKPT_AES:
                    verifyDukptAes(p);
                    break;
                case TR31:
                    verifyTr31(p);
                    break;
                case EMV:
                    verifyEmv(p);
                    break;
                case PIN:
                    verifyPin(p);
                    break;
                case SECURE_MESSAGING:
                    verifySecureMessaging(p);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown profile type: " + p.getType());
            }

            if (p.getExpectedResult() == PaymentProfile.ExpectedResult.FAILURE) {
                return new VerificationResult(false, "Profile expected FAILURE but succeeded unexpectedly.");
            }

            return new VerificationResult(true, "Profile verified successfully.");

        } catch (Exception e) {
            if (p.getExpectedResult() == PaymentProfile.ExpectedResult.SUCCESS) {
                return new VerificationResult(false, "Profile expected SUCCESS but failed: " + e.getMessage());
            } else {
                // Passed negative test. Verify the error fragment.
                String expectedFragment = p.getExpectedErrorFragment();
                if (expectedFragment != null && !expectedFragment.isEmpty()) {
                    String errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
                    if (errorMessage.contains(expectedFragment)) {
                        return new VerificationResult(true, "Negative profile verified successfully. Caught expected error: " + errorMessage);
                    } else {
                        return new VerificationResult(false, "Profile expected error containing '" + expectedFragment + "' but got '" + errorMessage + "'");
                    }
                } else {
                    return new VerificationResult(true, "Negative profile verified successfully.");
                }
            }
        }
    }

    private static void verifyDukptTdes(PaymentProfile p) throws Exception {
        String bdk = p.getInput("bdk");
        String ksn = p.getInput("ksn");
        if (ksn.length() > 20) throw new IllegalArgumentException("KSN too long for TDES");
        if (bdk.length() != 32) throw new IllegalArgumentException("BDK length invalid");

        String ipek = DukptKsn.deriveIpek(bdk, ksn);

        if (p.getOutputs().containsKey("ipek")) {
            if (!p.getOutput("ipek").equalsIgnoreCase(ipek)) {
                throw new Exception("IPEK mismatch");
            }
        }
        if (p.getOutputs().containsKey("workingKey")) {
            String usage = p.getParameter("usage");
            DukptKsn.TdesKeyUsage keyUsage = usage != null && usage.toLowerCase().contains("mac")
                    ? DukptKsn.TdesKeyUsage.MAC_REQUEST : DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
            String workingKey = DukptKsn.deriveWorkingKey(ipek, ksn, keyUsage).workingKeyHex();
            if (!p.getOutput("workingKey").equalsIgnoreCase(workingKey)) {
                throw new Exception("DUKPT working key mismatch");
            }
        }
    }

    private static void verifyDukptAes(PaymentProfile p) throws Exception {
        String bdk = p.getInput("bdk");
        String ksn = p.getInput("ksn");
        if (ksn.length() != 24) {
            throw new IllegalArgumentException("KSN size mismatch for AES DUKPT");
        }

        String scheme = p.getParameter("scheme");
        if (scheme != null && scheme.contains("192") && bdk.length() != 48) {
            throw new IllegalArgumentException("BDK size mismatch for AES-192");
        } else if (scheme != null && scheme.contains("256") && bdk.length() != 64) {
            throw new IllegalArgumentException("BDK size mismatch for AES-256");
        }

        if (p.getOutputs().containsKey("workingKey")) {
            AesDukpt.KeyUsage usageEnum;
            String usage = p.getParameter("usage");
            if (usage.contains("MAC")) {
                usageEnum = AesDukpt.KeyUsage.MAC_GENERATION;
            } else if (usage.contains("Data") || usage.contains("data")) {
                usageEnum = AesDukpt.KeyUsage.DATA_ENCRYPTION_ENCRYPT;
            } else {
                usageEnum = AesDukpt.KeyUsage.PIN_ENCRYPTION;
            }

            AesDukpt.DerivedKey derivedKey = AesDukpt.deriveWorkingKey(bdk, ksn, usageEnum, AesDukpt.KeyType.fromBytes(bdk.length() / 2));
            if (!p.getOutput("workingKey").equalsIgnoreCase(derivedKey.workingKeyHex())) {
                throw new Exception("AES Working key mismatch");
            }
        }
    }

    private static void verifyTr31(PaymentProfile p) throws Exception {
        String kbpk = p.getInput("kbpk");
        String keyToWrap = p.getInput("keyToWrap");

        char version = p.getParameter("version").charAt(0);
        char algorithm = p.getParameter("algorithm").charAt(0);
        String usage = p.getParameter("usage").substring(0, 2);
        char mode = p.getParameter("mode").charAt(0);
        char exportability = p.getParameter("exportability").charAt(0);
        String optionalBlocks = p.getInput("optionalBlocks") != null ? p.getInput("optionalBlocks") : "";

        String block = TR31Operations.wrapKey(kbpk, keyToWrap, usage, version, algorithm, mode, exportability, optionalBlocks);

        if (p.getOutputs().containsKey("header")) {
            if (!block.startsWith(p.getOutput("header"))) {
                throw new Exception("Header mismatch: expected " + p.getOutput("header") + " but got " + block.substring(0, Math.min(block.length(), p.getOutput("header").length())));
            }
        }

        if (p.getOutputs().containsKey("keyBlock")) {
            if (!block.equalsIgnoreCase(p.getOutput("keyBlock"))) {
                throw new Exception("TR-31 block mismatch");
            }
        }

        // Unwrap and verify
        String unwrappedKey = TR31Operations.unwrapKey(kbpk, block);
        if (!keyToWrap.equalsIgnoreCase(unwrappedKey)) {
            throw new Exception("Unwrapped key mismatch: expected " + keyToWrap + " but got " + unwrappedKey);
        }
    }

    private static void verifyEmv(PaymentProfile p) throws Exception {
        String imk = p.getInput("imk");
        String pan = p.getInput("pan");
        String panSeq = p.getInput("panSeq");
        String atc = p.getInput("atc");

        if (pan.length() < 12) throw new IllegalArgumentException("PAN too short");
        if (imk.length() < 32) throw new IllegalArgumentException("IMK too short");

        String iccMasterKey = EMVOperations.deriveICCMasterKey(imk, pan, panSeq);
        String sessionKey = EMVOperations.deriveSessionKey(iccMasterKey, atc, "");

        System.out.println("EMV ICC Master Key: " + iccMasterKey);
        System.out.println("EMV Session Key: " + sessionKey);

        if (p.getOutputs().containsKey("iccMasterKey")) {
            if (!p.getOutput("iccMasterKey").equalsIgnoreCase(iccMasterKey)) {
                throw new Exception("ICC Master Key mismatch");
            }
        }
        if (p.getOutputs().containsKey("sessionKey")) {
            if (!p.getOutput("sessionKey").equalsIgnoreCase(sessionKey)) {
                throw new Exception("Session Key mismatch");
            }
        }

        if (p.getInputs().containsKey("transactionData")) {
            String txnData = p.getInput("transactionData");
            String arqc = EMVOperations.generateARQC(sessionKey, txnData, 1);
            System.out.println("EMV ARQC: " + arqc);
            if (p.getOutputs().containsKey("arqc")) {
                if (!p.getOutput("arqc").equalsIgnoreCase(arqc)) {
                    throw new Exception("ARQC mismatch");
                }
            }
        }

        if (p.getInputs().containsKey("arqc") && p.getInputs().containsKey("arc")) {
            String arqc = p.getInput("arqc");
            String arc = p.getInput("arc");
            String arpc = EMVOperations.generateARPC_Method1(sessionKey, arqc, arc);
            if (p.getOutputs().containsKey("arpc")) {
                if (!p.getOutput("arpc").equalsIgnoreCase(arpc)) {
                    throw new Exception("ARPC mismatch");
                }
            }
        }
    }

    private static void verifyPin(PaymentProfile p) throws Exception {
        String format = p.getParameter("format");
        if (format == null) {
            throw new IllegalArgumentException("PIN profile missing mandatory 'format' parameter");
        }

        if ("Translate".equals(p.getParameter("method"))) {
            String inPinBlock = p.getInput("inPinBlock");
            String inKey = p.getInput("inKey");
            String outKey = p.getInput("outKey");
            String inPan = p.getInput("inPan");
            String outPan = p.getInput("outPan");
            String inFormat = p.getInput("inFormat");
            String outFormat = p.getInput("outFormat");

            if (inKey.length() < 16) throw new IllegalArgumentException("inKey too short");
            if (outKey.length() < 16) throw new IllegalArgumentException("outKey too short");

            // Decrypt with inKey
            byte[] inPinBlockBytes = DataConverter.hexToBytes(inPinBlock);
            byte[] inKeyBytes = DataConverter.hexToBytes(inKey);
            byte[] clearInBlockBytes = PaymentOperations.decryptDesEcb(inPinBlockBytes, inKeyBytes);
            String clearInBlock = DataConverter.bytesToHex(clearInBlockBytes);

            String clearOutBlock = PaymentOperations.translatePinBlock(clearInBlock, inPan != null ? inPan : outPan, inFormat, outFormat);

            byte[] outKeyBytes = DataConverter.hexToBytes(outKey);
            byte[] clearOutBlockBytes = DataConverter.hexToBytes(clearOutBlock);
            byte[] outPinBlockBytes = PaymentOperations.encryptDesEcb(clearOutBlockBytes, outKeyBytes);
            String calculatedOutBlock = DataConverter.bytesToHex(outPinBlockBytes);

            String expectedOutBlock = p.getOutput("outPinBlock");
            if (expectedOutBlock == null) {
                // Try alternate name
                expectedOutBlock = p.getOutput("pinBlock");
            }
            if (expectedOutBlock != null && !calculatedOutBlock.equalsIgnoreCase(expectedOutBlock)) {
                throw new IllegalStateException("Translation failed. Expected: " + expectedOutBlock + ", Got: " + calculatedOutBlock);
            }
        } else {
            String pan = p.getInput("pan");
            String key = p.getInput("key");
            String pin = p.getInput("pin") != null ? p.getInput("pin") : p.getOutput("pin");
            if (pin != null && pin.length() > 12) throw new IllegalArgumentException("PIN length must be <= 12");
            if (format.contains("0") && (pan == null || pan.length() < 12)) throw new IllegalArgumentException("Format 0 requires PAN");

            if (p.getInputs().containsKey("pinBlock") && key != null && p.getOutputs().containsKey("pin")) {
                // Test decryption instead
                byte[] keyBytes = DataConverter.hexToBytes(key);
                byte[] encBytes = DataConverter.hexToBytes(p.getInput("pinBlock"));
                byte[] clearBytes = PaymentOperations.decryptDesEcb(encBytes, keyBytes);
                String decClearBlock = DataConverter.bytesToHex(clearBytes).toUpperCase();

                String expectedFormatName = "Format " + format.replace("ISO ", "") + " (ISO-" + format.replace("ISO ", "") + ")";
                String decodedPin = PaymentOperations.decodePinBlock(decClearBlock, pan == null ? "" : pan, expectedFormatName);

                if (!p.getOutput("pin").equals(decodedPin)) {
                    throw new Exception("Decoded PIN mismatch");
                }
            } else if (key != null && p.getOutputs().containsKey("pinBlock")) {
                String clearPinBlock = PaymentOperations.encodePinBlock(pin, pan == null ? "" : pan, "Format " + format.replace("ISO ", "") + " (ISO-" + format.replace("ISO ", "") + ")");
                byte[] keyBytes = DataConverter.hexToBytes(key);
                byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);
                byte[] encBytes = PaymentOperations.encryptDesEcb(clearBytes, keyBytes);
                String pinBlock = DataConverter.bytesToHex(encBytes).toUpperCase();

                if (!p.getOutput("pinBlock").equalsIgnoreCase(pinBlock)) {
                    throw new Exception("PIN block mismatch");
                }
            }
        }
    }

    private static void verifySecureMessaging(PaymentProfile p) throws Exception {
        String sessionKey = p.getInput("sessionKey");
        String command = p.getInput("apdu");

        if (sessionKey == null || command == null) throw new IllegalArgumentException("Missing sessionKey or apdu");
        if (sessionKey.length() < 16) throw new IllegalArgumentException("Session Key too short");

        String mac = EMVOperations.generateScriptMAC(sessionKey, command);

        if (p.getOutputs().containsKey("mac")) {
            if (!p.getOutput("mac").equalsIgnoreCase(mac)) {
                throw new Exception("Script MAC mismatch");
            }
        }
    }
}
