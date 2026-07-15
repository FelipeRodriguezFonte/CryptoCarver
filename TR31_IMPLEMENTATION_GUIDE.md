# TR-31 IMPLEMENTATION GUIDE
## Complete Guide for CryptoCarver

---

## 📚 REFERENCIAS ESENCIALES

### Estándares
- **ANSI X9.143-2022**: "Interoperable Secure Key Exchange Key Block Specification"
- **ASC X9 TR 31-2018**: Versión original (supersedida por X9.143)
- **ANSI X9.24-1**: Retail Financial Services Symmetric Key Management

### Implementaciones de Referencia
1. **psec (Python)**: https://github.com/knovichikhin/psec
   - `psec.tr31.wrap()` y `psec.tr31.unwrap()`
   - Implementación completa y validada

2. **openemv/tr31 (C)**: https://github.com/openemv/tr31
   - Implementación completa con herramienta CLI
   - Soporta versiones A, B, C, D, E

3. **IBM CCA Documentation**: https://www.ibm.com/docs/en/linux-on-systems?topic=programming-tr-31-symmetric-key-management

---

## 🏗️ ESTRUCTURA TR-31 KEY BLOCK

```
TR-31 Key Block = Header + Encrypted Key Data + MAC

Example: B0080P0TE00N0000471D4FBE35E5865BDE20DBF4C15503161F55D681170BF8DD14D01B6822EF8550CB67C569DE8AC048
         |--Header--||------------------------Encrypted Key Data-----------------------||-------MAC-------|
```

### 1. HEADER (ASCII, clear text)

```
Position  Field                    Length  Example  Description
--------  -----------------------  ------  -------  ----------------------------------
0         Version ID                1      B        A, B, C, D, or E
1-4       Key Block Length          4      0080     Total length in ASCII characters
5-6       Key Usage                 2      P0       PIN Encryption
7         Algorithm                 1      T        T=TDES, A=AES, R=RSA, E=ECDSA, H=HMAC
8         Mode of Use               1      E        E=Encrypt, D=Decrypt, B=Both, etc.
9-10      Key Version Number        2      00       00-99
11        Exportability             1      N        E=Exportable, N=Non-exportable, S=Sensitive
12-13     Num Optional Blocks       2      00       00-99 optional blocks
14-15     Reserved                  2      00       Usually "00"
16+       Optional Blocks           Var    (none)   See Optional Blocks section
```

### 2. ENCRYPTED KEY DATA

**Format depends on Version ID:**

#### Version A (TDES, no length obfuscation):
```
Clear Key Data = Key Length (2 bytes) + Key (variable)
Encrypted = TDES-ECB(KBPK, Clear Key Data)
```

#### Version B (TDES, with length obfuscation):
```
Clear Key Data = Key (variable) + Padding (to 8-byte block)
Encrypted = TDES-CBC(KBPK, Clear Key Data, IV=zeros)
```

#### Version C (TDES, with length obfuscation):
Similar to B but with enhanced security

#### Version D (AES, with length obfuscation):
```
Clear Key Data = Key (variable) + Padding (to 16-byte block)
Encrypted = AES-CBC(KBPK, Clear Key Data, IV=zeros)
```

#### Version E (AES-KWP - Key Wrap with Padding):
Uses NIST SP 800-38F AES Key Wrap with Padding

### 3. MAC (Message Authentication Code)

**Purpose**: Integrity protection of Header + Encrypted Key Data

**Algorithm depends on Version:**

#### Version A & C:
```
MAC = CMAC-TDES(KBMK, Header + Encrypted Key Data)
Take leftmost 4 bytes (8 hex characters)
```

#### Version B:
```
MAC = CMAC-TDES(KBMK, Header + Encrypted Key Data)
Take leftmost 8 bytes (16 hex characters)
```

#### Version D & E:
```
MAC = CMAC-AES(KBMK, Header + Encrypted Key Data)
Take leftmost 8 bytes (16 hex characters)
```

**KBMK (Key Block MAC Key)**:
- Derived from KBPK using key derivation
- Version B/C/D: KBMK = Derive(KBPK, 0x02)
- Version A: KBMK = KBPK (same key)

---

## 🔑 KEY USAGE CODES

```
Code   Description                          Common Use Cases
----   ----------------------------------   ------------------------------------
B0     Base Derivation Key (BDK)            DUKPT key hierarchy
B1     Initial DUKPT Key                    Initial key for DUKPT
B2     Base Key Variant Key                 Key variant operations
D0     Data Encryption Key                  Symmetric data encryption
D1     Asymmetric Key for Data              RSA/ECDSA for data encryption
D2     Data Encryption Key for Decimals     Financial data encryption
D3     Data Encryption Key for PINs         PIN block encryption (deprecated, use P0)
K0     Key Encryption/Wrapping Key (KEK)    Wrapping other keys
K1     TR-31 KBPK                           TR-31 Key Block Protection Key
K2     TR-34 Asymmetric Key for KEK         TR-34 asymmetric wrapping
M0     ISO 16609 MAC Algorithm 1 (HMAC)     HMAC generation
M1     ISO 9797-1 MAC Algorithm 1           CBC-MAC
M2     ISO 9797-1 MAC Algorithm 2           Not commonly used
M3     ISO 9797-1 MAC Algorithm 3           Retail MAC (ANSI X9.19)
M4     ISO 9797-1 MAC Algorithm 4           Not commonly used
M5     ISO 9797-1 MAC Algorithm 5           CMAC
M6     ISO 9797-1 MAC Algorithm 6           Not commonly used
M7     HMAC                                 HMAC-SHA256, HMAC-SHA512
M8     ISO 9797-1 MAC Algorithm 5 (CMAC)    CMAC variant
P0     PIN Encryption Key                   PIN block encryption
S0     Asymmetric key for digital signature DSA/ECDSA/RSA signatures
S1     Asymmetric key pair for CA           Certificate Authority key
S2     Asymmetric key pair for other        General asymmetric operations
V0     PIN Verification Key (KPV)           IBM 3624 PIN verification
V1     CVV/CVC Key                          Card Verification Value
V2     Card Verification Key                Other card verification
```

---

## 🔐 ALGORITHMS & MODES

### Algorithms
- **T**: Triple DES (TDES) - 16 or 24 bytes
- **A**: AES - 16, 24, or 32 bytes
- **R**: RSA - Variable (public/private key pair)
- **E**: ECDSA - Variable (elliptic curve)
- **H**: HMAC - Variable (HMAC key)

### Modes of Use
- **E**: Encrypt only
- **D**: Decrypt only
- **B**: Both encrypt and decrypt
- **G**: Generate only (e.g., MAC generation only)
- **V**: Verify only (e.g., MAC verification only)
- **C**: Both generate and verify
- **S**: Signature generation only
- **V**: Signature verification only
- **N**: No special restrictions
- **X**: Key derivation

---

## 📦 OPTIONAL BLOCKS

Optional blocks provide additional metadata. Each block has:
- **Block ID** (2 chars)
- **Block Length** (2 hex digits)
- **Block Data** (variable)

### Common Optional Blocks

#### **KS** - Key Set Identifier
```
Format: KS + Length (2 hex) + KSI (10 hex digits)
Example: KS0AFFFF00A0200001E00000
Purpose: Identifies the key set/domain
```

#### **KC** - KCV (Key Check Value)
```
Format: KC + Length (2 hex) + KCV (variable)
Example: KC0C000169E3  (5 bytes KCV for AES CMAC)
Purpose: Key verification without exposing key
```

#### **KP** - KCV of KBPK
```
Format: KP + Length (2 hex) + KCV (variable)
Example: KP06ABCDEF
Purpose: Verify correct KBPK is used
```

#### **PB** - Padding Block
```
Format: PB + Length (2 hex) + Random Data
Purpose: Obfuscate header length
```

#### **TS** - Time Stamp
```
Format: TS + Length (2 hex) + ISO8601 timestamp
Example: TS102025-12-08T11:30:00Z
Purpose: Key creation/expiry time
```

#### **CT** - Certificate
```
Format: CT + Length (2 hex) + Certificate data
Purpose: X.509 certificate for asymmetric keys
```

#### **DA** - Derivation Allowed
```
Format: DA + Length (2 hex) + Derivation allowed flag
Example: DA0200  (derivation allowed)
Purpose: Control key derivation
```

#### **HM** - HMAC Hash Algorithm
```
Format: HM + Length (2 hex) + Hash algorithm code
Example: HM0201  (SHA-256)
Values: 01=SHA-1, 02=SHA-256, 03=SHA-512
Purpose: Specify HMAC hash algorithm
```

---

## 🛠️ IMPLEMENTATION ALGORITHM

### WRAP (Export) Algorithm

```java
public static String wrapKey(byte[] kbpk, String header, byte[] key) throws Exception {
    // Step 1: Parse or build header
    TR31Header h = TR31Header.parse(header);
    
    // Step 2: Prepare key data based on version
    byte[] keyData;
    if (h.versionId.equals("A")) {
        // Version A: Length prefix + Key
        keyData = new byte[2 + key.length];
        keyData[0] = (byte)((key.length >> 8) & 0xFF);
        keyData[1] = (byte)(key.length & 0xFF);
        System.arraycopy(key, 0, keyData, 2, key.length);
    } else if (h.versionId.equals("B") || h.versionId.equals("C")) {
        // Version B/C: Key + Padding to 8-byte block
        int paddedLen = ((key.length + 7) / 8) * 8;
        keyData = new byte[paddedLen];
        System.arraycopy(key, 0, keyData, 0, key.length);
        // Fill remaining with random for security
        SecureRandom random = new SecureRandom();
        if (paddedLen > key.length) {
            byte[] padding = new byte[paddedLen - key.length];
            random.nextBytes(padding);
            System.arraycopy(padding, 0, keyData, key.length, padding.length);
        }
    } else if (h.versionId.equals("D")) {
        // Version D: Key + Padding to 16-byte block
        int paddedLen = ((key.length + 15) / 16) * 16;
        keyData = new byte[paddedLen];
        System.arraycopy(key, 0, keyData, 0, key.length);
        SecureRandom random = new SecureRandom();
        if (paddedLen > key.length) {
            byte[] padding = new byte[paddedLen - key.length];
            random.nextBytes(padding);
            System.arraycopy(padding, 0, keyData, key.length, padding.length);
        }
    } else {
        throw new UnsupportedOperationException("Version " + h.versionId + " not supported");
    }
    
    // Step 3: Encrypt key data
    byte[] encryptedKey;
    if (h.versionId.equals("A")) {
        // ECB mode
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        encryptedKey = cipher.doFinal(keyData);
    } else if (h.versionId.equals("B") || h.versionId.equals("C")) {
        // CBC mode with zero IV
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(new byte[8]); // Zero IV
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        encryptedKey = cipher.doFinal(keyData);
    } else if (h.versionId.equals("D")) {
        // AES-CBC with zero IV
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]); // Zero IV
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
        encryptedKey = cipher.doFinal(keyData);
    } else {
        throw new UnsupportedOperationException("Version " + h.versionId + " not supported");
    }
    
    // Step 4: Derive MAC key (KBMK)
    byte[] kbmk;
    if (h.versionId.equals("A")) {
        kbmk = kbpk; // Version A uses same key
    } else {
        // Derive MAC key using constant 0x02
        byte[] derivationData = new byte[16];
        Arrays.fill(derivationData, (byte)0x02);
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        kbmk = cipher.doFinal(derivationData);
    }
    
    // Step 5: Calculate MAC
    String headerStr = h.build();
    String encryptedKeyHex = DataConverter.bytesToHex(encryptedKey);
    String macInput = headerStr + encryptedKeyHex;
    
    byte[] mac;
    if (h.algorithm.equals("A")) {
        // AES-CMAC
        Mac cmac = Mac.getInstance("AESCMAC", "BC");
        SecretKeySpec macKey = new SecretKeySpec(kbmk, "AES");
        cmac.init(macKey);
        byte[] fullMac = cmac.doFinal(macInput.getBytes());
        mac = Arrays.copyOf(fullMac, 8); // Take first 8 bytes
    } else {
        // TDES-CMAC
        Mac cmac = Mac.getInstance("DESEDECMAC", "BC");
        SecretKeySpec macKey = new SecretKeySpec(kbmk, "DESede");
        cmac.init(macKey);
        byte[] fullMac = cmac.doFinal(macInput.getBytes());
        if (h.versionId.equals("A") || h.versionId.equals("C")) {
            mac = Arrays.copyOf(fullMac, 4); // 4 bytes for A/C
        } else {
            mac = Arrays.copyOf(fullMac, 8); // 8 bytes for B
        }
    }
    
    // Step 6: Assemble key block
    String keyBlock = headerStr + encryptedKeyHex + DataConverter.bytesToHex(mac);
    
    // Step 7: Update header length
    h.keyBlockLength = keyBlock.length();
    keyBlock = h.build() + encryptedKeyHex + DataConverter.bytesToHex(mac);
    
    return keyBlock;
}
```

### UNWRAP (Import) Algorithm

```java
public static byte[] unwrapKey(byte[] kbpk, String keyBlock) throws Exception {
    // Step 1: Parse header
    TR31Header h = TR31Header.parse(keyBlock);
    
    // Step 2: Validate length
    if (keyBlock.length() != h.keyBlockLength) {
        throw new IllegalArgumentException("Key block length mismatch");
    }
    
    // Step 3: Extract encrypted key and MAC
    int headerLen = h.build().length();
    int macLen = (h.versionId.equals("A") || h.versionId.equals("C")) ? 8 : 16; // hex chars
    
    String encryptedKeyHex = keyBlock.substring(headerLen, keyBlock.length() - macLen);
    String macHex = keyBlock.substring(keyBlock.length() - macLen);
    
    byte[] encryptedKey = DataConverter.hexToBytes(encryptedKeyHex);
    byte[] providedMac = DataConverter.hexToBytes(macHex);
    
    // Step 4: Verify MAC
    byte[] kbmk;
    if (h.versionId.equals("A")) {
        kbmk = kbpk;
    } else {
        // Derive MAC key
        byte[] derivationData = new byte[16];
        Arrays.fill(derivationData, (byte)0x02);
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        kbmk = cipher.doFinal(derivationData);
    }
    
    String macInput = h.build() + encryptedKeyHex;
    byte[] calculatedMac;
    
    if (h.algorithm.equals("A")) {
        Mac cmac = Mac.getInstance("AESCMAC", "BC");
        SecretKeySpec macKey = new SecretKeySpec(kbmk, "AES");
        cmac.init(macKey);
        byte[] fullMac = cmac.doFinal(macInput.getBytes());
        calculatedMac = Arrays.copyOf(fullMac, providedMac.length);
    } else {
        Mac cmac = Mac.getInstance("DESEDECMAC", "BC");
        SecretKeySpec macKey = new SecretKeySpec(kbmk, "DESede");
        cmac.init(macKey);
        byte[] fullMac = cmac.doFinal(macInput.getBytes());
        calculatedMac = Arrays.copyOf(fullMac, providedMac.length);
    }
    
    if (!Arrays.equals(calculatedMac, providedMac)) {
        throw new SecurityException("MAC verification failed - key block may be corrupted or tampered");
    }
    
    // Step 5: Decrypt key data
    byte[] keyData;
    if (h.versionId.equals("A")) {
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        keyData = cipher.doFinal(encryptedKey);
    } else if (h.versionId.equals("B") || h.versionId.equals("C")) {
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(new byte[8]);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        keyData = cipher.doFinal(encryptedKey);
    } else if (h.versionId.equals("D")) {
        SecretKeySpec keySpec = new SecretKeySpec(kbpk, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        keyData = cipher.doFinal(encryptedKey);
    } else {
        throw new UnsupportedOperationException("Version " + h.versionId + " not supported");
    }
    
    // Step 6: Extract actual key
    if (h.versionId.equals("A")) {
        // Extract key from length prefix
        int keyLen = ((keyData[0] & 0xFF) << 8) | (keyData[1] & 0xFF);
        return Arrays.copyOfRange(keyData, 2, 2 + keyLen);
    } else {
        // Key length obfuscation - need to determine actual key length
        // This requires knowledge of the algorithm
        int expectedKeyLen = getExpectedKeyLength(h.algorithm);
        return Arrays.copyOf(keyData, expectedKeyLen);
    }
}

private static int getExpectedKeyLength(String algorithm) {
    switch (algorithm) {
        case "T": return 24; // TDES (can be 16 or 24)
        case "A": return 32; // AES (can be 16, 24, or 32)
        default: throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
    }
}
```

---

## 🧪 TEST VECTORS

### Test Vector 1: Version B, TDES PIN Encryption Key

```
KBPK:     AB2E09DB3EF0BA71E0CE6CD755C23A3B (16 bytes TDES)
Key:      BF82DAC6A33DF92CE66E15B70E5DCEB6 (16 bytes TDES)
Usage:    P0 (PIN Encryption)
Mode:     E (Encrypt only)
Version:  00
Export:   N (Non-exportable)

Expected Output:
B0096P0TE00N0000471D4FBE35E5865BDE20DBF4C15503161F55D681170BF8DD14D01B6822EF8550CB67C569DE8AC048

Breakdown:
Header:           B0096P0TE00N0000
Encrypted Key:    471D4FBE35E5865BDE20DBF4C15503161F55D681170BF8DD14D01B6822EF8550
MAC:              CB67C569DE8AC048
```

### Test Vector 2: Version D, AES Data Encryption Key

```
KBPK:     88E1AB2A2E3DD38C1FA039A536500CC8A87AB9D62DC92C01058FA79F44657DE6
Key:      3F419E1CB7079442AA37474C2EFBF8B8
Usage:    D0 (Data Encryption)
Mode:     B (Both)
Version:  00
Export:   E (Exportable)

Expected Output:
D0104D0AB00E0000E2A14AB28094B1CE0DD80C0BF2B9FC47A1E88B4AC91F7624B11AA66FF0DD50A2F2C4DC1CC92D8B4E6082568AA6C

Breakdown:
Header:           D0104D0AB00E0000
Encrypted Key:    E2A14AB28094B1CE0DD80C0BF2B9FC47A1E88B4AC91F7624B11AA66FF0DD50A2F2C4DC1C
MAC:              C92D8B4E6082568AA6C
```

---

## 📋 INTEGRATION STEPS

### 1. Add to Keys Tab UI

Edit `fxml/main.fxml` or create `fxml/tr31.fxml`:

```xml
<!-- TR-31 Import Section -->
<VBox spacing="10">
    <Label text="TR-31 Import" styleClass="section-label"/>
    <HBox spacing="10">
        <Label text="KBPK:" prefWidth="100"/>
        <TextField fx:id="tr31KbpkImport" promptText="Key Block Protection Key (hex)"/>
    </HBox>
    <HBox spacing="10">
        <Label text="Key Block:" prefWidth="100"/>
        <TextField fx:id="tr31KeyBlock" promptText="TR-31 Key Block"/>
    </HBox>
    <Button text="Import Key" onAction="#handleTR31Import"/>
</VBox>

<!-- TR-31 Export Section -->
<VBox spacing="10">
    <Label text="TR-31 Export" styleClass="section-label"/>
    <HBox spacing="10">
        <Label text="KBPK:" prefWidth="100"/>
        <TextField fx:id="tr31KbpkExport" promptText="Key Block Protection Key (hex)"/>
    </HBox>
    <HBox spacing="10">
        <Label text="Key:" prefWidth="100"/>
        <TextField fx:id="tr31KeyToWrap" promptText="Key to wrap (hex)"/>
    </HBox>
    <HBox spacing="10">
        <Label text="Key Usage:" prefWidth="100"/>
        <ComboBox fx:id="tr31UsageCombo"/>
    </HBox>
    <HBox spacing="10">
        <Label text="Algorithm:" prefWidth="100"/>
        <ComboBox fx:id="tr31AlgorithmCombo"/>
    </HBox>
    <Button text="Export Key" onAction="#handleTR31Export"/>
</VBox>

<TextArea fx:id="tr31ResultArea" editable="false"/>
```

### 2. Add Controller Methods

In `KeysController.java`:

```java
private TextField tr31KbpkImport;
private TextField tr31KeyBlock;
private TextField tr31KbpkExport;
private TextField tr31KeyToWrap;
private ComboBox<String> tr31UsageCombo;
private ComboBox<String> tr31AlgorithmCombo;
private TextArea tr31ResultArea;

public void handleTR31Import() {
    try {
        String kbpk = tr31KbpkImport.getText().trim();
        String keyBlock = tr31KeyBlock.getText().trim();
        
        String unwrappedKey = TR31Operations.unwrapKey(kbpk, keyBlock);
        
        StringBuilder result = new StringBuilder();
        result.append("TR-31 IMPORT RESULT\\n");
        result.append("====================\\n\\n");
        result.append("Unwrapped Key: ").append(unwrappedKey).append("\\n");
        
        tr31ResultArea.setText(result.toString());
        mainController.updateStatus("TR-31 key imported successfully");
        
    } catch (Exception e) {
        tr31ResultArea.setText("Error: " + e.getMessage());
        mainController.updateStatus("TR-31 import failed");
    }
}

public void handleTR31Export() {
    try {
        String kbpk = tr31KbpkExport.getText().trim();
        String key = tr31KeyToWrap.getText().trim();
        String usage = tr31UsageCombo.getValue();
        String algorithm = tr31AlgorithmCombo.getValue();
        
        String keyBlock = TR31Operations.wrapKey(kbpk, key, usage, 
            algorithm.charAt(0), 'B', false);
        
        StringBuilder result = new StringBuilder();
        result.append("TR-31 EXPORT RESULT\\n");
        result.append("====================\\n\\n");
        result.append("Key Block: ").append(keyBlock).append("\\n");
        
        tr31ResultArea.setText(result.toString());
        mainController.updateStatus("TR-31 key exported successfully");
        
    } catch (Exception e) {
        tr31ResultArea.setText("Error: " + e.getMessage());
        mainController.updateStatus("TR-31 export failed");
    }
}
```

### 3. Populate ComboBoxes

```java
private void setupTR31Controls() {
    tr31UsageCombo.getItems().addAll(
        "P0 - PIN Encryption",
        "D0 - Data Encryption",
        "K0 - Key Encryption",
        "M0 - MAC Generation",
        "V1 - CVV/CVC"
    );
    
    tr31AlgorithmCombo.getItems().addAll(
        "T - Triple DES",
        "A - AES"
    );
}
```

---

## ✅ VALIDATION CHECKLIST

- [ ] Implement Version A (TDES, no obfuscation)
- [ ] Implement Version B (TDES, with obfuscation)
- [ ] Implement Version D (AES, with obfuscation)
- [ ] Test with provided test vectors
- [ ] Validate against psec Python library
- [ ] Add error handling for invalid headers
- [ ] Add MAC verification
- [ ] Add optional blocks support (KS, KC, KP)
- [ ] Add UI integration in Keys tab
- [ ] Add operation history logging

---

## 🎯 RECOMMENDED APPROACH

1. **Start with Version B (most common)**
   - TDES-based
   - Length obfuscation
   - Good for learning

2. **Validate with psec**
   ```python
   import psec.tr31
   
   kbpk = bytes.fromhex("AB2E09DB3EF0BA71E0CE6CD755C23A3B")
   key = bytes.fromhex("BF82DAC6A33DF92CE66E15B70E5DCEB6")
   
   kb = psec.tr31.wrap(kbpk, "B0096P0TE00N0000", key)
   print(kb)
   
   unwrapped = psec.tr31.unwrap(kbpk, kb)
   print(unwrapped.hex())
   ```

3. **Test edge cases**
   - Different key lengths
   - Invalid MACs
   - Corrupted headers
   - Missing optional blocks

4. **Add Version D (AES)**
   - Similar to B but with AES
   - 16-byte blocks instead of 8

5. **Add Optional Blocks**
   - Start with KC (KCV)
   - Add KS (Key Set ID)
   - Add TS (Timestamp)

---

## 📚 ADDITIONAL RESOURCES

- **GitHub - openemv/tr31**: Excellent C implementation with examples
- **psec Documentation**: https://github.com/knovichikhin/psec
- **ANSI X9.24-1-2017**: Standard document (purchase required)
- **IBM CCA TR-31**: Free documentation with examples

---

**Good luck with the implementation!** 🚀

Felipe, this is a complex standard but very valuable for key management.
Start with Version B, validate with psec, then expand to other versions.

Contact me if you need clarification on any part!
