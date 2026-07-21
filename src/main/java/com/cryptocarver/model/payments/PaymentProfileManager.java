package com.cryptocarver.model.payments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the available payment profiles, providing built-in laboratory examples.
 */
public class PaymentProfileManager {

    private static final List<PaymentProfile> profiles = new ArrayList<>();

    static {
        initializeLaboratoryProfiles();
    }

    private static void initializeLaboratoryProfiles() {
        // --- DUKPT TDES ---
        profiles.add(new PaymentProfile.Builder("dukpt_tdes_pos_1", "DUKPT TDES - Basic PIN", "1.0", PaymentProfile.ProfileType.DUKPT_TDES)
            .description("Standard TDES PIN derivation")
            .addParameter("scheme", "DUKPT TDES")
            .addParameter("usage", "PIN encryption")
            .addInput("bdk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("ksn", "FFFF9876543210E00000")
            .addOutput("ipek", "6AC292FAA1315B4D858AB3A3D7D5933A")
            .addOutput("workingKey", "6AC292FAA1315BB2858AB3A3D7D593C5") // PIN encryption variant
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_tdes_pos_2", "DUKPT TDES - MAC", "1.0", PaymentProfile.ProfileType.DUKPT_TDES)
            .description("Standard TDES MAC derivation")
            .addParameter("scheme", "DUKPT TDES")
            .addParameter("usage", "MAC request")
            .addInput("bdk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("ksn", "FFFF9876543210E00001") // Count = 1
            .addOutput("workingKey", "042666B4918430A368DE9628D03984C9") // MAC request variant
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_tdes_neg_1", "DUKPT TDES - Invalid KSN", "1.0", PaymentProfile.ProfileType.DUKPT_TDES)
            .description("KSN is too long for TDES")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("KSN")
            .addParameter("scheme", "DUKPT TDES")
            .addInput("bdk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("ksn", "FFFF9876543210E000000000") // 12-byte KSN
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_tdes_neg_2", "DUKPT TDES - Invalid BDK", "1.0", PaymentProfile.ProfileType.DUKPT_TDES)
            .description("BDK is wrong size")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("BDK length")
            .addParameter("scheme", "DUKPT TDES")
            .addInput("bdk", "0123456789ABCDEF") // Single length
            .addInput("ksn", "FFFF9876543210E00000")
            .build()
        );

        // --- DUKPT AES ---
        profiles.add(new PaymentProfile.Builder("dukpt_aes_pos_1", "DUKPT AES-128 - Data Enc", "1.0", PaymentProfile.ProfileType.DUKPT_AES)
            .description("AES DUKPT Data Encryption")
            .addParameter("scheme", "DUKPT AES-128")
            .addParameter("usage", "Data encryption (encrypt)")
            .addInput("bdk", "0123456789ABCDEF0123456789ABCDEF")
            .addInput("ksn", "FFFF9876543210E000000000")
            .addOutput("workingKey", "AC7F7B30C13C0435C6643E9000A3E1AF") // Calculated X9.24-3
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_aes_pos_2", "DUKPT AES-128 - MAC", "1.0", PaymentProfile.ProfileType.DUKPT_AES)
            .description("AES DUKPT MAC Generation")
            .addParameter("scheme", "DUKPT AES-128")
            .addParameter("usage", "MAC generation")
            .addInput("bdk", "0123456789ABCDEF0123456789ABCDEF")
            .addInput("ksn", "FFFF9876543210E000000001") // Count = 1
            .addOutput("workingKey", "0AE7C0EAD9231C80811FDD8F8D93F633")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_aes_neg_1", "DUKPT AES - Invalid BDK", "1.0", PaymentProfile.ProfileType.DUKPT_AES)
            .description("BDK size mismatch")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("BDK size mismatch")
            .addParameter("scheme", "DUKPT AES-192")
            .addParameter("usage", "PIN encryption")
            .addInput("bdk", "0123456789ABCDEF0123456789ABCDEF") // 128-bit key for AES-192
            .addInput("ksn", "FFFF9876543210E000000000")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("dukpt_aes_neg_2", "DUKPT AES - Invalid KSN", "1.0", PaymentProfile.ProfileType.DUKPT_AES)
            .description("KSN size mismatch")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("KSN size mismatch")
            .addParameter("scheme", "DUKPT AES-128")
            .addParameter("usage", "PIN encryption")
            .addInput("bdk", "0123456789ABCDEF0123456789ABCDEF")
            .addInput("ksn", "FFFF9876543210E00000") // 10-byte KSN
            .build()
        );

        // --- TR-31 ---
        profiles.add(new PaymentProfile.Builder("tr31_pos_1", "TR-31 DUKPT Key (TDES)", "1.0", PaymentProfile.ProfileType.TR31)
            .description("Export TDES DUKPT key using AES KBPK")
            .addParameter("version", "D (AES KBPK)")
            .addParameter("algorithm", "T (TDES)")
            .addParameter("usage", "B1 (Initial DUKPT Key)")
            .addParameter("mode", "E (Encryption/Decryption)")
            .addParameter("exportability", "S (Sensitive)")
            .addInput("kbpk", "00112233445566778899AABBCCDDEEFF")
            .addInput("keyToWrap", "0123456789ABCDEFFEDCBA9876543210")
            .addOutput("header", "D0112B1TE00S0000") // Correct TR-31 header layout
            .build()
        );

        profiles.add(new PaymentProfile.Builder("tr31_pos_2", "TR-31 MAC Key (AES)", "1.0", PaymentProfile.ProfileType.TR31)
            .description("Export AES MAC key using AES KBPK")
            .addParameter("version", "D (AES KBPK)")
            .addParameter("algorithm", "A (AES)")
            .addParameter("usage", "M3 (MAC Key)")
            .addParameter("mode", "G (Generate)")
            .addParameter("exportability", "E (Exportable)")
            .addInput("kbpk", "00112233445566778899AABBCCDDEEFF")
            .addInput("keyToWrap", "11111111111111111111111111111111")
            .addOutput("header", "D0112M3AG00E0000")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("tr31_pos_2", "TR-31 Optional Blocks", "1.0", PaymentProfile.ProfileType.TR31)
            .description("Generate and unwrap TR-31 block with optional blocks")
            .addParameter("version", "D (AES KBPK)")
            .addParameter("algorithm", "A (AES)")
            .addParameter("usage", "D0 (Data Encryption)")
            .addParameter("mode", "B (Both Encrypt/Decrypt)")
            .addParameter("exportability", "N (Non-exportable)")
            .addInput("kbpk", "00112233445566778899AABBCCDDEEFF")
            .addInput("keyToWrap", "11111111111111111111111111111111")
            .addInput("optionalBlocks", "0100KS02ABCD") // 1 block, reserved 00, ID KS, len 02, data ABCD
            .addOutput("header", "D0120D0AB00N0100KS02ABCD")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("tr31_neg_1", "TR-31 Invalid Mode (Symmetric)", "1.0", PaymentProfile.ProfileType.TR31)
            .description("Impossible combination: Symmetric algorithm cannot use asymmetric signature mode")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("Impossible combination")
            .addParameter("version", "B (TDES KBPK)")
            .addParameter("algorithm", "T (TDES)")
            .addParameter("usage", "D0 (Data Encryption)")
            .addParameter("mode", "S (Signature Only)") // Invalid combo for TDES
            .addParameter("exportability", "E (Exportable)")
            .addInput("kbpk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("keyToWrap", "11111111111111111111111111111111")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("tr31_neg_2", "TR-31 Invalid Algorithm", "1.0", PaymentProfile.ProfileType.TR31)
            .description("Impossible combination: AES with TDES version")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("AES algorithm")
            .addParameter("version", "B (TDES KBPK)")
            .addParameter("algorithm", "A (AES)")
            .addParameter("usage", "M3 (MAC Key)")
            .addParameter("mode", "G (Generate)")
            .addParameter("exportability", "E (Exportable)")
            .addInput("kbpk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("keyToWrap", "11111111111111111111111111111111")
            .build()
        );

        // --- EMV ---
        profiles.add(new PaymentProfile.Builder("emv_pos_1", "EMV ARQC (Option A)", "1.0", PaymentProfile.ProfileType.EMV)
            .description("Generate ARQC using Option A")
            .addParameter("method", "Option A")
            .addInput("pan", "4512345678901234")
            .addInput("panSeq", "01")
            .addInput("imk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("atc", "0001")
            .addInput("unpredictableNumber", "12345678")
            .addInput("transactionData", "000000001000000000000000097800000000000009781911220012345678")
            .addOutput("iccMasterKey", "D942A14951D1F58D1762D692E7977977")
            .addOutput("sessionKey", "46E5C745FBFE70E919FE762A3B40BCE9")
            .addOutput("arqc", "0F3432AE696C3331")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("emv_pos_2", "EMV ARPC (Method 1)", "1.0", PaymentProfile.ProfileType.EMV)
            .description("Generate ARPC Method 1")
            .addParameter("method", "Option A")
            .addInput("pan", "4512345678901234")
            .addInput("panSeq", "01")
            .addInput("imk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("atc", "0001")
            .addInput("arqc", "0F3432AE696C3331")
            .addInput("arc", "3030") // "00"
            .addOutput("arpc", "7AD7FD77FDB4853D")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("emv_neg_1", "EMV ARQC - Invalid PAN", "1.0", PaymentProfile.ProfileType.EMV)
            .description("Invalid PAN length for Option A")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("PAN too short")
            .addParameter("method", "Option A")
            .addInput("pan", "451") // Too short
            .addInput("panSeq", "01")
            .addInput("imk", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("atc", "0001")
            .addInput("transactionData", "00")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("emv_neg_2", "EMV ARQC - Invalid IMK", "1.0", PaymentProfile.ProfileType.EMV)
            .description("Invalid IMK length")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("IMK too short")
            .addParameter("method", "Option A")
            .addInput("pan", "4512345678901234")
            .addInput("panSeq", "01")
            .addInput("imk", "012345") // Too short
            .addInput("atc", "0001")
            .addInput("transactionData", "00")
            .build()
        );

        // --- PIN ---
        profiles.add(new PaymentProfile.Builder("pin_pos_1", "PIN - ISO Format 0", "1.0", PaymentProfile.ProfileType.PIN)
            .description("Encrypt PIN block Format 0")
            .addParameter("format", "ISO 0")
            .addInput("pin", "1234")
            .addInput("pan", "4512345678901234")
            .addInput("key", "0123456789ABCDEFFEDCBA9876543210")
            .addOutput("pinBlock", "BF5D44D08D106392")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("pin_pos_2", "PIN - ISO Format 1", "1.0", PaymentProfile.ProfileType.PIN)
            .description("Decrypt PIN block Format 1")
            .addParameter("format", "ISO 1")
            .addInput("pinBlock", "84622D8B01578243")
            .addInput("key", "0123456789ABCDEFFEDCBA9876543210")
            .addOutput("pin", "1234")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("pin_neg_1", "PIN - Invalid Length", "1.0", PaymentProfile.ProfileType.PIN)
            .description("PIN length > 12")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("PIN length must be <= 12")
            .addParameter("format", "ISO 0")
            .addInput("pin", "1234567890123") // >12
            .addInput("pan", "4512345678901234")
            .addInput("key", "0123456789ABCDEFFEDCBA9876543210")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("pin_neg_2", "PIN - Missing PAN", "1.0", PaymentProfile.ProfileType.PIN)
            .description("Format 0 requires PAN")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("Format 0 requires PAN")
            .addParameter("format", "ISO 0")
            .addInput("pin", "1234")
            .addInput("key", "0123456789ABCDEFFEDCBA9876543210")
            .build()
        );

        // --- SECURE MESSAGING ---
        profiles.add(new PaymentProfile.Builder("sm_pos_1", "Secure Messaging - Script MAC", "1.0", PaymentProfile.ProfileType.SECURE_MESSAGING)
            .description("Generate Script MAC")
            .addParameter("algorithm", "ISO 9797-1 MAC Algorithm 3")
            .addParameter("padding", "ISO 7816-4")
            .addInput("sessionKey", "46E5C745FBFE70E919FE762A3B40BCE9")
            .addInput("apdu", "8424000008")
            .addOutput("mac", "385FA0CEE01633C9")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("sm_pos_2", "Secure Messaging - Command MAC", "1.0", PaymentProfile.ProfileType.SECURE_MESSAGING)
            .description("Verify APDU MAC")
            .addParameter("algorithm", "ISO 9797-1 MAC Algorithm 3")
            .addParameter("padding", "ISO 7816-4")
            .addInput("sessionKey", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("apdu", "84240000080BFFF5DF3FAA24E1")
            .addOutput("mac", "33811407DDAD00AD")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("sm_neg_1", "SM - Invalid Key", "1.0", PaymentProfile.ProfileType.SECURE_MESSAGING)
            .description("Invalid key size")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("Session Key too short")
            .addParameter("algorithm", "AES")
            .addInput("sessionKey", "0123")
            .addInput("apdu", "8424000008")
            .build()
        );

        profiles.add(new PaymentProfile.Builder("sm_neg_2", "SM - Empty APDU", "1.0", PaymentProfile.ProfileType.SECURE_MESSAGING)
            .description("Empty APDU")
            .expectedResult(PaymentProfile.ExpectedResult.FAILURE)
            .expectedErrorFragment("must not be empty")
            .addParameter("algorithm", "AES")
            .addInput("sessionKey", "0123456789ABCDEFFEDCBA9876543210")
            .addInput("apdu", "")
            .build()
        );
    }

    public static List<PaymentProfile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    public static List<PaymentProfile> getProfilesByType(PaymentProfile.ProfileType type) {
        return profiles.stream().filter(p -> p.getType() == type).collect(Collectors.toList());
    }
}
