package com.cryptoforge.crypto;

/**
 * Builder para construir headers TR-31 de forma sencilla
 *
 * IMPORTANTE - Compatibilidad KBPK/Versión:
 * - Versión A/B/C: Requiere KBPK de 8, 16 o 24 bytes (DES, 2-key TDES, 3-key
 * TDES)
 * - Versión D: Requiere KBPK de 16, 24 o 32 bytes (AES-128, AES-192, AES-256)
 *
 * Ejemplo de uso:
 * // Para versión B (TDES)
 * String kbpk = "DA92D6A23B04A81CD05E7FA2089815D68C01B68F1C3BE961"; // 24 bytes
 * String header = new HeaderBuilder()
 * .version('B')
 * .keyUsage("P0")
 * .algorithm('T')
 * .build();
 *
 * // Para versión D (AES)
 * String kbpk =
 * "5DD6D976AEE98552A440089DF8404C8C385BD51C5E1C9702EF7AF23EB632517F"; // 32
 * bytes
 * String header = new HeaderBuilder()
 * .version('D')
 * .keyUsage("D0")
 * .algorithm('A')
 * .build();
 */
public class HeaderBuilder {
    private char version = 'B'; // Default: Version B
    private String keyUsage = "P0"; // Default: PIN Encryption
    private char algorithm = 'T'; // Default: TDES
    private char modeOfUse = 'E'; // Default: Encrypt only
    private String keyVersion = "00"; // Default: No version
    private char exportability = 'E'; // Default: Exportable
    private String optionalBlocks = ""; // Default: No optional blocks

    public HeaderBuilder version(char version) {
        if (version != 'A' && version != 'B' && version != 'C' && version != 'D') {
            throw new IllegalArgumentException("Version must be A, B, C, or D");
        }
        this.version = version;
        return this;
    }

    /**
     * Key Usage (2 caracteres)
     * Ejemplos comunes:
     * - B0: BDK (Base Derivation Key)
     * - C0: CVK (Card Verification Key)
     * - D0: Data Encryption (symmetric)
     * - D1: Asymmetric key for data encryption
     * - I0: Initialization Vector
     * - K0: Key Encryption/Wrapping
     * - M0: ISO 16609 MAC (algorithm 1)
     * - M1: ISO 9797-1 MAC (algorithm 1)
     * - M3: ISO 9797-1 MAC (algorithm 3)
     * - M6: ISO 9797-1 MAC (algorithm 5, CMAC)
     * - P0: PIN Encryption
     * - S0: Asymmetric key for digital signature
     * - V0: PIN verification (other algorithm)
     * - V1: PIN verification (IBM 3624)
     * - V2: PIN verification (VISA PVV)
     */
    public HeaderBuilder keyUsage(String keyUsage) {
        if (keyUsage.length() != 2) {
            throw new IllegalArgumentException("Key Usage must be 2 characters");
        }
        this.keyUsage = keyUsage.toUpperCase();
        return this;
    }

    /**
     * Algorithm (1 carácter)
     * - A: AES
     * - D: DEA (DES)
     * - E: Elliptic Curve
     * - H: HMAC
     * - R: RSA
     * - S: DSA
     * - T: Triple DEA (TDES)
     */
    public HeaderBuilder algorithm(char algorithm) {
        this.algorithm = Character.toUpperCase(algorithm);
        return this;
    }

    /**
     * Mode of Use (1 carácter)
     * - B: Both encrypt & decrypt / generate & verify
     * - C: Both sign & decrypt (asymmetric)
     * - D: Decrypt / verify only
     * - E: Encrypt / generate only
     * - G: Generate only (key derivation)
     * - N: No special restrictions
     * - S: Signature only
     * - T: Both sign & key transport (asymmetric)
     * - V: Verify only (signature)
     * - X: Key derivation
     * - Y: Create cryptographic checksum
     */
    public HeaderBuilder modeOfUse(char modeOfUse) {
        this.modeOfUse = Character.toUpperCase(modeOfUse);
        return this;
    }

    /**
     * Key Version Number (2 caracteres)
     * - "00": No version
     * - "01"-"99": Version numbers
     */
    public HeaderBuilder keyVersion(String keyVersion) {
        if (keyVersion.length() != 2) {
            throw new IllegalArgumentException("Key Version must be 2 characters");
        }
        this.keyVersion = keyVersion;
        return this;
    }

    /**
     * Exportability (1 carácter)
     * - E: Exportable
     * - N: Non-exportable
     * - S: Sensitive (non-exportable under any conditions)
     */
    public HeaderBuilder exportability(char exportability) {
        this.exportability = Character.toUpperCase(exportability);
        return this;
    }

    /**
     * Optional Blocks (longitud variable)
     * Si está vacío, se usa "0000"
     *
     * Formato completo: "NNRRBB..." donde:
     * - NN = número de bloques (2 dígitos hex, ej: "01" para 1 bloque)
     * - RR = reserved (normalmente "00")
     * - BB... = datos de los bloques
     *
     * Cada bloque tiene formato: ID (2 chars) + Length (2 chars hex) + Data
     *
     * Ejemplo:
     * - "0000" = sin optional blocks (default)
     * - "0100KS0600ABCDEF123456" = 1 bloque Key Set ID con 6 bytes de datos
     *
     * NOTA: El usuario debe proporcionar el string completo incluyendo el contador
     * y el byte reserved. Para la mayoría de casos, usar "0000" (sin bloques).
     */
    public HeaderBuilder optionalBlocks(String optionalBlocks) {
        this.optionalBlocks = optionalBlocks.toUpperCase();
        return this;
    }

    /**
     * Construye el header TR-31 (sin la longitud, que se calcula al hacer wrap)
     */
    public String build() {
        StringBuilder header = new StringBuilder();

        // Version ID (1 char)
        header.append(version);

        // Length (4 chars) - placeholder, se actualiza en wrap
        header.append("0000");

        // Key Usage (2 chars)
        header.append(keyUsage);

        // Algorithm (1 char)
        header.append(algorithm);

        // Mode of Use (1 char)
        header.append(modeOfUse);

        // Key Version Number (2 chars)
        header.append(keyVersion);

        // Exportability (1 char)
        header.append(exportability);

        // Optional Blocks (variable length, min 4 chars for "0000")
        // Format: "NNBB..." where NN = number of blocks (00 if none), BB... = block
        // data
        if (optionalBlocks.isEmpty()) {
            header.append("0000");
        } else {
            // User provides complete optional blocks string including count
            header.append(optionalBlocks);
        }

        return header.toString();
    }

    /**
     * Atajos para casos comunes
     *
     * NOTA: Estos atajos generan el header, pero debes asegurarte de usar
     * el tipo correcto de KBPK:
     * - Métodos *TDES: Requieren KBPK de 8, 16 o 24 bytes (versión A/B/C)
     * - Métodos *AES: Requieren KBPK de 16, 24 o 32 bytes (versión D)
     */
    public static class Common {
        // PIN Encryption Key (TDES) - Requiere KBPK de 16 o 24 bytes
        public static String pinEncryptionTDES() {
            return new HeaderBuilder()
                    .version('B')
                    .keyUsage("P0")
                    .algorithm('T')
                    .modeOfUse('E')
                    .exportability('E')
                    .build();
        }

        // Data Encryption Key (AES) - Requiere KBPK de 16, 24 o 32 bytes
        public static String dataEncryptionAES() {
            return new HeaderBuilder()
                    .version('D')
                    .keyUsage("D0")
                    .algorithm('A')
                    .modeOfUse('B')
                    .exportability('E')
                    .build();
        }

        // MAC Key (TDES) - Requiere KBPK de 16 o 24 bytes
        public static String macKeyTDES() {
            return new HeaderBuilder()
                    .version('B')
                    .keyUsage("M0")
                    .algorithm('T')
                    .modeOfUse('G')
                    .exportability('E')
                    .build();
        }

        // CVV Key - Requiere KBPK de 16 o 24 bytes
        public static String cvvKey() {
            return new HeaderBuilder()
                    .version('B')
                    .keyUsage("C0")
                    .algorithm('T')
                    .modeOfUse('G')
                    .exportability('E')
                    .build();
        }

        // Key Encryption Key (KEK) - Requiere KBPK de 16, 24 o 32 bytes
        public static String kek() {
            return new HeaderBuilder()
                    .version('D')
                    .keyUsage("K0")
                    .algorithm('A')
                    .modeOfUse('B')
                    .exportability('E')
                    .build();
        }
    }
}
