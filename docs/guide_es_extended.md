# CryptoCarver - Guía Técnica Completa

**Versión 2.5.0** - Documentación Técnica Extendida

Este documento proporciona una guía técnica exhaustiva sobre CryptoCarver, detallando todas las implementaciones criptográficas, cumplimiento de estándares y detalles de implementación.

---

## Índice de Contenidos

1. [Arquitectura del Software](#arquitectura)
2. [Módulo Generic - Utilidades](#modulo-generic)
3. [Módulo Cipher - Criptografía Simétrica](#modulo-cipher)
4. [Módulo Keys - Gestión de Claves](#modulo-keys)
5. [Módulo Payments - Algoritmos de Pago](#modulo-payments)
6. [Módulo EMV](#modulo-emv)
7. [Módulo PIN - Bloques PIN](#modulo-pin)
8. [Módulo MAC - Códigos de Autenticación](#modulo-mac)
9. [Módulo Signature - Firmas Digitales](#modulo-signature)
10. [Módulo ASN.1 - Parser](#modulo-asn1)
11. [Módulo CMS/PKCS#7](#modulo-cms)
12. [Módulo JOSE - JWT/JWS/JWE](#modulo-jose)
13. [Vectores de Verificación](#vectores)

---

## 1. Arquitectura del Software {#arquitectura}

### 1.1 Stack Tecnológico

**Lenguaje y Runtime:**
- Java 17 LTS
- JavaFX 21.0.5 para UI
- Maven para gestión de dependencias

**Librerías Criptográficas:**
- **BouncyCastle 1.78.1**: Provider criptográfico extendido
  - Algoritmos adicionales no disponibles en JCA estándar
  - Soporte para formatos PKCS, X.509, CMS
- **Apache Commons Codec 1.17.1**: Encoding/decoding utilities

**Arquitectura MVC:**
```
CryptoCarver/
├── ui/                    # Controllers (Vista)
│   ├── MainController.java
│   ├── GenericController.java
│   ├── CipherController.java
│   └── ...
├── crypto/                # Modelo (Lógica criptográfica)
│   ├── SymmetricCipher.java
│   ├── HashOperations.java
│   └── ...
├── utils/                 # Utilidades
│   ├── DataConverter.java
│   └── PaddingUtil.java
└── model/                 # Modelos de datos
    ├── HistoryItem.java
    └── SavedSession.java
```

### 1.2 Providers Criptográficos

**BouncyCastle Provider (BC):**
```java
Security.addProvider(new BouncyCastleProvider());

// Prioridad en uso:
1. Intentar algoritmo con "BC"
2. Fallback a provider default de JCA
3. Reportar error si no disponible
```

**Algoritmos por Provider:**
- JCA Default: SHA-256, AES, RSA básico
- BC Required: RIPEMD160, Ed25519, CMAC, algunos modos GCM

---

## 2. Módulo Generic - Utilidades Criptográficas {#modulo-generic}

### 2.1 Funciones Hash

**Clase:** `HashOperations.java`

**Algoritmos Soportados:**
| Algoritmo | Output (bytes) | Estado | Uso |
|-----------|----------------|--------|-----|
| MD5 | 16 | Deprecated | Legacy only |
| SHA-1 | 20 | Deprecated | Legacy only |
| SHA-224 | 28 | OK | Uso específico |
| SHA-256 | 32 | ✓ Recomendado | General |
| SHA-384 | 48 | OK | Alta seguridad |
| SHA-512 | 64 | OK | Alta seguridad |
| SHA3-256 | 32 | OK | SHA-3 family |
| SHA3-512 | 64 | OK | SHA-3 family |
| SHAKE128 | Variable | OK | XOF |
| SHAKE256 | Variable | OK | XOF |
| RIPEMD160 | 20 | OK | Bitcoin/legacy |

**Implementación:**
```java
public static byte[] calculateHash(byte[] data, String algorithm) {
    MessageDigest md = MessageDigest.getInstance(algorithm, "BC");
    return md.digest(data);
}
```

**Uso desde UI:**
1. Seleccionar algoritmo en dropdown
2. Ingresar datos (Text, Hex o Base64)
3. Resultado en formato seleccionado

### 2.2 Aritmética Modular

**Clase:** `ModularArithmetic.java`

**Operaciones:**
```java
// Exponenciación modular: (base^exp) mod m
BigInteger result = base.modPow(exponent, modulus);

// Inverso modular: (a * x) ≡ 1 (mod m)
BigInteger inverse = a.modInverse(modulus);

// GCD (Máximo común divisor)
BigInteger gcd = a.gcd(b);

// Generación de primos
BigInteger prime = BigInteger.probablePrime(bitLength, random);
```

**Aplicaciones:**
- Cálculos RSA (generación de claves, cifrado)
- Diffie-Hellman
- Verificación de parámetros criptográficos

### 2.3 Generación de UUIDs

**Clase:** `UUIDGenerator.java`

**Métodos:**
```java
// UUID v4 estándar: 550e8400-e29b-41d4-a716-446655440000
String uuid = generateUUID();

// Sin guiones: 550e8400e29b41d4a716446655440000
String compactUuid = generateUUIDWithoutHyphens();

// Mayúsculas
String upperUuid = generateUppercaseUUID();

// Múltiples UUIDs
String[] uuids = generateMultipleUUIDs(10);
```

### 2.4 Check Digits

**Clase:** `CheckDigitCalculator.java`

**Algoritmos:**

**Luhn (Mod 10):**
- Uso: Números de tarjeta
- Detecta errores simples y transposiciones

**Verhoeff:**
- Detecta todos errores de transposición
- Más robusto que Luhn

**Damm:**
- Detección de errores
- Tabla quasigroup

```java
int checkDigit = CheckDigitCalculator.calculateCheckDigit(data, "LUHN");
```

---

## 3. Módulo Cipher - Criptografía Simétrica {#modulo-cipher}

### 3.1 Algoritmos y Modos

**Clase:** `SymmetricCipher.java`

**Matriz de Soporte:**
| Algoritmo | Tamaño Clave | Tamaño Bloque | Modos | Estado |
|-----------|--------------|---------------|-------|--------|
| DES | 64 bits (56 efectivos) | 64 bits | ECB, CBC, CFB, OFB | Obsoleto |
| 3DES | 128/192 bits | 64 bits | ECB, CBC, CFB, OFB | Legacy |
| AES | 128/192/256 bits | 128 bits | ECB, CBC, CTR, GCM, CFB, OFB | ✓ Recomendado |

**Padding Schemes:**
| Padding | Descripción | Uso |
|---------|-------------|-----|
| NoPadding | Datos exactos múltiplo bloque | CTR, GCM |
| PKCS5/PKCS7 | Estándar RFC 5652 | General |
| ISO10126 | Random + length byte | Legacy |
| ISO7816-4 | 0x80 + 0x00s | Smart cards |
| Zeros | Padding con 0x00 | Custom |

**Implementación Cifrado:**
```java
public static byte[] encrypt(
    byte[] data,
    byte[] key,
    String algorithm,  // "AES", "DESede"
    String mode,       // "CBC", "GCM"
    String padding,    // "PKCS5Padding"
    byte[] iv          // null para ECB
) throws Exception {
    
    String transformation = algorithm + "/" + mode + "/" + padding;
    Cipher cipher = Cipher.getInstance(transformation, "BC");
    
    SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
    
    if (iv != null && !mode.equals("ECB")) {
        if (mode.equals("GCM")) {
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        } else {
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        }
    } else {
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
    }
    
    return cipher.doFinal(data);
}
```

### 3.2 Modo GCM (Authenticated Encryption)

**Características:**
- AEAD: Autenticación + Cifrado
- Tag de autenticación: 128 bits
- Nonce (IV): 96 bits recomendado
- Associated Data (AAD): Opcional

**Uso:**
```java
// Cifrado con AAD:
GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
cipher.updateAAD(associatedData);  // Opcional
byte[] ciphertext = cipher.doFinal(plaintext);

// Resultado: ciphertext incluye authentication tag
```

**IMPORTANTE:** Nunca reutilizar nonce con misma clave.

### 3.3 Gestión de IVs

**Generación Segura:**
```java
SecureRandom random = new SecureRandom();
byte[] iv = new byte[blockSize];
random.nextBytes(iv);
```

**Tamaños:**
- DES/3DES: 8 bytes
- AES: 16 bytes
- GCM: 12 bytes (96 bits) recomendado

---

## 4. Módulo Keys - Gestión de Claves {#modulo-keys}

### 4.1 Generación de Claves Simétricas

**Clase:** `KeyOperations.java`

**Métodos:**
```java
// DES: 8 bytes con odd parity
byte[] desKey = generateKey("DES", true);

// 3DES 2-key: 16 bytes
byte[] tdesKey2 = generateKey("3DES-2KEY", true);

// 3DES 3-key: 24 bytes
byte[] tdesKey3 = generateKey("3DES-3KEY", true);

// AES: 16/24/32 bytes
byte[] aesKey128 = generateKey("AES-128");
byte[] aesKey256 = generateKey("AES-256");
```

**Odd Parity para DES:**
```java
// Ajustar LSB de cada byte para parity impar
for (int i = 0; i < key.length; i++) {
    int parity = Integer.bitCount(key[i] & 0xFF);
    if (parity % 2 == 0) {
        key[i] ^= 0x01;  // Flip LSB
    }
}
```

### 4.2 Key Check Value (KCV)

**Métodos Implementados:**

**VISA (Estándar):**
```java
// Cifrar bloque de ceros, tomar primeros 3 bytes
byte[] zeros = new byte[8];
byte[] encrypted = encrypt(zeros, key, algorithm);
byte[] kcv = Arrays.copyOf(encrypted, 3);

// Ejemplo:
// Key: 0123456789ABCDEFFEDCBA9876543210
// KCV: 08D7B4
```

**IBM, ATALLA, FUTUREX, SHA-256, CMAC, AES:**
- Cada método con su algoritmo específico
- Implementados en `KeyOperations.java`

### 4.3 Key Component Splitting (XOR)

**Proceso:**
```java
public static List<byte[]> splitKey(byte[] key, int numComponents) {
    List<byte[]> components = new ArrayList<>();
    SecureRandom random = new SecureRandom();
    
    // Generar N-1 componentes aleatorios
    for (int i = 0; i < numComponents - 1; i++) {
        byte[] comp = new byte[key.length];
        random.nextBytes(comp);
        components.add(comp);
    }
    
    // Último componente: key XOR todos los anteriores
    byte[] last = Arrays.copyOf(key, key.length);
    for (byte[] comp : components) {
        for (int i = 0; i < key.length; i++) {
            last[i] ^= comp[i];
        }
    }
    components.add(last);
    
    return components;
}
```

**Recombinación:**
```java
// XOR todos los componentes
byte[] key = new byte[components.get(0).length];
for (byte[] comp : components) {
    for (int i = 0; i < key.length; i++) {
        key[i] ^= comp[i];
    }
}
```

### 4.4 TR-31 Key Blocks

**Clases:** `TR31.java`, `TR31Operations.java`, `HeaderBuilder.java`

**Estructura TR-31:**
```
[Header 16 chars][Encrypted Key][MAC 8 bytes]

Header: VLLLLKKAAVVEE0000
V = Version (B,C,D,E)
LLLL = Length (hex)
KK = Key Usage
A = Algorithm
A = Mode of Use
VV = Version Number
E = Exportability
0000 = Optional Blocks Length
```

**Key Usage Codes:**
```
B0: BDK (DUKPT)
C0: CVK
D0: Data Encryption (symmetric)
K0: Key Encryption
K1: KBPK
M0-M7: MAC keys (varios algoritmos)
P0: PIN Encryption
V0-V2: PIN Verification
S0: Firma digital
E0: EMV Master Key
```

**Wrap Key:**
```java
String keyBlock = TR31Operations.wrapKey(
    kbpk,           // Key Block Protection Key
    key,            // Key to wrap
    "P0",           // Usage
    'D',            // Version
    'T',            // Algorithm (3DES)
    'E',            // Mode (Encrypt only)
    'N'             // Exportability (Non-exportable)
);
```

**Unwrap Key:**
```java
String unwrappedKey = TR31Operations.unwrapKey(kbpk, keyBlock);
```

### 4.5 Key Derivation

**PBKDF2:**
```java
public static byte[] deriveKeyPBKDF2(
    char[] password,
    byte[] salt,
    int iterations,
    int keyLength,
    String algorithm  // "SHA256", "SHA384", "SHA512"
) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength * 8);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(
        "PBKDF2WithHmac" + algorithm, "BC"
    );
    return factory.generateSecret(spec).getEncoded();
}
```

**Parámetros Recomendados:**
- Salt: 16+ bytes aleatorios
- Iterations: 600,000+ (OWASP 2023)
- Algorithm: SHA256

**HKDF:**
```java
// Extract:
byte[] prk = hkdfExtract(salt, inputKeyMaterial, "SHA256");

// Expand:
byte[] derivedKey = hkdfExpand(prk, info, outputLength, "SHA256");
```

### 4.6 Generación de Claves Asimétricas

**RSA:**
```java
KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC");
gen.initialize(2048, new SecureRandom());
KeyPair pair = gen.generateKeyPair();
```

**ECDSA:**
```java
KeyPairGenerator gen = KeyPairGenerator.getInstance("EC", "BC");
ECGenParameterSpec spec = new ECGenParameterSpec("secp256r1");  // P-256
gen.initialize(spec, new SecureRandom());
KeyPair pair = gen.generateKeyPair();
```

**Ed25519:**
```java
KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519", "BC");
KeyPair pair = gen.generateKeyPair();
```

---

## 5. Módulo Payments - Algoritmos de Pago {#modulo-payments}

### 5.1 CVV/CVC Generation

**Clase:** `PaymentOperations.java`

**Algoritmo CVV (Visa/MasterCard):**
```
1. Construir bloque: PAN || Expiry || Service Code (32 hex chars)
2. Dividir: DataHigh (16), DataLow (16)
3. Block1 = DES_Encrypt(CVKA, DataHigh)
4. Block2 = Block1 XOR DataLow
5. Result = 3DES_Encrypt(CVKA, CVKB, CVKA, Block2)
6. Decimalization: extraer dígitos 0-9
7. CVV = primeros 3 dígitos
```

**Implementación:**
```java
public static String generateCVV(
    String cvkA,        // 16 hex chars
    String cvkB,        // 16 hex chars
    String pan,         // hasta 19 dígitos
    String expiry,      // YYMM
    String serviceCode  // 3 dígitos (101, 201, 999 para iCVV)
) throws Exception {
    
    // 1. Construir bloque de 32 chars
    String block = padLeft(pan + expiry + serviceCode, 32, '0');
    
    // 2. Split
    String dataHigh = block.substring(0, 16);
    String dataLow = block.substring(16, 32);
    
    // 3. DES encrypt dataHigh con CVKA
    byte[] block1 = desEncrypt(hexToBytes(cvkA), hexToBytes(dataHigh));
    
    // 4. XOR
    byte[] block2 = xor(block1, hexToBytes(dataLow));
    
    // 5. 3DES EDE
    byte[] result = tdesEDE(hexToBytes(cvkA), hexToBytes(cvkB), block2);
    
    // 6-7. Decimalization
    String cvv = decimalize(result, 3);
    
    return cvv;
}
```

**Variaciones:**
- **iCVV**: Service Code = 999
- **CVV2**: Impreso en tarjeta (mismas claves diferentes)

### 5.2 Dynamic CVV (dCVV)

```java
public static String generateDCVV(
    String cvkA,
    String cvkB,
    String pan,
    String panSeq,      // 2 dígitos
    String expiryDate,
    String serviceCode,
    String atc          // Application Transaction Counter (4 hex)
) throws Exception {
    // Similar a CVV pero incluye ATC en construcción del bloque
    String block = pan + panSeq + expiry + serviceCode + atc;
    // ... resto similar
}
```

### 5.3 PIN Verification Value (PVV)

**VISA PVV Algorithm:**
```java
public static String generatePVV(
    String pin,         // 4-12 dígitos
    String pan,         // PAN
    String pvk,         // PIN Verification Key (16 bytes)
    String pvki,        // PVK Index (1 dígito)
    int pvvLength       // Típicamente 4
) throws Exception {
    
    // 1. Construir: PIN || PAN(11 rightmost) || PVKI
    String panPart = pan.substring(pan.length() - 12, pan.length() - 1);  // 11 dígitos sin check
    String tsi = pin + panPart + pvki;
    
    // 2. Cifrar con PVK (3DES)
    byte[] encrypted = tdesEncrypt(hexToBytes(pvk), tsi.getBytes());
    
    // 3. Decimalization
    String pvv = decimalize(encrypted, pvvLength);
    
    return pvv;
}
```

### 5.4 IBM 3624 PIN Offset

**Natural PIN:**
```java
public static String generateIBM3624Pin(
    String pan,
    String pvk,
    String decTable,    // "0123456789012345" o custom
    String offset
) throws Exception {
    
    // 1. Cifrar PAN con PVK
    byte[] encrypted = tdesEncrypt(hexToBytes(pvk), hexToBytes(pan));
    
    // 2. Decimalization con tabla
    String naturalPin = decimalizeWithTable(encrypted, decTable, 4);
    
    // 3. Aplicar offset
    String customerPin = applyOffset(naturalPin, offset);
    
    return customerPin;
}
```

**Offset Calculation:**
```java
public static String generateIBM3624Offset(
    String pin,
    String pan,
    String pvk,
    String decTable
) throws Exception {
    
    String naturalPin = generateIBM3624Pin(pan, pvk, decTable, "0000");
    
    // Offset = (PIN - Natural) mod 10
    StringBuilder offset = new StringBuilder();
    for (int i = 0; i < pin.length(); i++) {
        int p = Character.getNumericValue(pin.charAt(i));
        int n = Character.getNumericValue(naturalPin.charAt(i));
        int o = (p - n + 10) % 10;
        offset.append(o);
    }
    
    return offset.toString();
}
```

---

## 6. Módulo EMV {#modulo-emv}

### 6.1 Session Key Derivation

**Clase:** `EMVOperations.java`

**EMV Option A:**
```java
public static String deriveSessionKey(
    String masterKey,   // 16 bytes (2-key 3DES)
    String pan,
    String panSeq,      // "00" - "99"
    String method       // "COMMON", "MASTERCARD", "VISA"
) throws Exception {
    
    // 1. Construir data: últimos 16 dígitos PAN + PAN Seq (3 dígitos)
    String panPart = pan.substring(Math.max(0, pan.length() - 16));
    String data = padRight(panPart + padLeft(panSeq, 3, '0'), 16, '0');
    
    // 2. Cifrar con MK (3DES ECB)
    Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(hexToBytes(masterKey), "DESede"));
    byte[] sessionKey = cipher.doFinal(hexToBytes(data));
    
    return bytesToHex(sessionKey);
}
```

### 6.2 ARQC Generation

**Algoritmo:** Retail MAC (ISO 9797-1 Alg 3)

```java
public static String generateARQC(
    String sk,              // Session Key
    String transactionData,
    int paddingMethod       // 0=ISO9797-1-Method2, 1=Method1
) throws Exception {
    
    // 1. Padding
    byte[] paddedData = addPadding(hexToBytes(transactionData), paddingMethod);
    
    // 2. CBC-MAC con primera mitad de SK
    byte[] k1 = Arrays.copyOfRange(hexToBytes(sk), 0, 8);
    byte[] k2 = Arrays.copyOfRange(hexToBytes(sk), 8, 16);
    
    // 3. Retail MAC
    byte[] arqc = retailMAC(paddedData, k1, k2);
    
    return bytesToHex(arqc);  // 8 bytes
}
```

### 6.3 ARPC Generation

**Method 1 (XOR):**
```java
public static String generateARPC_Method1(
    String sk,
    String arqc,    // 8 bytes hex
    String arc      // ASCII "00", "01", etc.
) throws Exception {
    
    // 1. Convertir ARC a hex y expandir a 8 bytes
    byte[] arcBytes = arc.getBytes();
    byte[] arcExpanded = new byte[8];
    System.arraycopy(arcBytes, 0, arcExpanded, 0, Math.min(arcBytes.length, 8));
    
    // 2. XOR ARQC con ARC
    byte[] xored = xor(hexToBytes(arqc), arcExpanded);
    
    // 3. 3DES encrypt
    Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(hexToBytes(sk), "DESede"));
    byte[] arpc = cipher.doFinal(xored);
    
    return bytesToHex(arpc);
}
```

**Method 2 (CSU):**
```java
public static String generateARPC_Method2(
    String sk,
    String arc,     // 1 byte
    String csu      // 4 bytes Card Status Update
) throws Exception {
    
    // 1. Construir bloque: ARC || CSU || Padding
    String block = arc + csu + "000000";  // Padding a 8 bytes
    
    // 2. Retail MAC
    byte[] arpc = retailMAC(hexToBytes(block), hexToBytes(sk));
    
    return bytesToHex(arpc);
}
```

### 6.4 Script MAC

```java
public static String generateScriptMAC(
    String sk,
    String script   // APDU command hex
) throws Exception {
    
    // Retail MAC sobre comando
    byte[] mac = retailMAC(hexToBytes(script), hexToBytes(sk));
    
    return bytesToHex(mac);
}
```

---

## 7. Módulo PIN - Bloques PIN {#modulo-pin}

### 7.1 Formatos PIN Block

**Clase:** `PinBlock.java`

**ISO Format 0:**
```
Estructura: 0L PP PP PP PP PP PP FF

L = Length (4-12)
PP = PIN digits
FF = Filler (0xF)

XOR con PAN Block: 0000 + [12 rightmost PAN digits without check]

Ejemplo:
PIN: 1234
Length: 4
PIN Payload: 04 12 34 FF FF FF FF FF

PAN: 4123450000001234
PAN Block: 00 00 34 50 00 00 01 23

PIN Block: 04 12 00 AF FF FF FE DC
```

```java
public static String encodeFormat0(String pin, String pan) {
    // 1. Construir PIN payload
    String payload = "0" + pin.length() + pin + "FFFFFFFFFFFFF";
    payload = payload.substring(0, 16);
    
    // 2. Construir PAN block
    String panPart = pan.substring(Math.max(0, pan.length() - 13), pan.length() - 1);
    String panBlock = "0000" + padLeft(panPart, 12, '0');
    
    // 3. XOR
    byte[] pinBlock = xor(hexToBytes(payload), hexToBytes(panBlock));
    
    return bytesToHex(pinBlock);
}
```

**ISO Format 1:**
```
Estructura: 1L PP PP PP PP RR RR RR

No XOR con PAN
Random padding
```

```java
public static String encodeFormat1(String pin) {
    StringBuilder payload = new StringBuilder();
    payload.append("1").append(pin.length()).append(pin);
    
    // Random padding
    SecureRandom random = new SecureRandom();
    while (payload.length() < 16) {
        int digit = random.nextInt(10);
        payload.append(digit);
    }
    
    return payload.toString();
}
```

**ISO Format 2:**
```
Similar a Format 0 pero sin XOR con PAN
```

**ISO Format 3:**
```
Similar a Format 1 pero CON XOR con PAN
```

**ISO Format 4:**
```
Estructura: 4L PP PP PP AA AA AA AA AA AA AA AA AA AA AA AA

32 hex chars (16 bytes) para AES compatibility
```

```java
public static String encodeFormat4(String pin, String pan) {
    // 1. PIN payload (32 chars / 16 bytes)
    String payload = "4" + pin.length() + pin;
    
    // Padding con dígitos aleatorios
    SecureRandom random = new SecureRandom();
    while (payload.length() < 32) {
        payload += random.nextInt(10);
    }
    
    // 2. PAN block extendido (16 bytes)
    String panPart = pan.substring(Math.max(0, pan.length() - 13), pan.length() - 1);
    String panBlock = "0000" + padLeft(panPart, 12, '0');
    panBlock = panBlock + panBlock;  // Repeat para 16 bytes
    
    // 3. XOR
    byte[] pinBlock = xor(hexToBytes(payload), hexToBytes(panBlock));
    
    return bytesToHex(pinBlock);
}
```

### 7.2 Decrypt PIN Block

```java
public static String decode(String pinBlock, String pan, String format) {
    switch (format) {
        case "ISO-0":
            // XOR reverso
            String panBlock = constructPANBlock(pan);
            byte[] clearPayload = xor(hexToBytes(pinBlock), hexToBytes(panBlock));
            
            // Extraer PIN
            String payload = bytesToHex(clearPayload);
            int length = Character.getNumericValue(payload.charAt(1));
            String pin = payload.substring(2, 2 + length);
            
            return pin;
            
        case "ISO-1":
            // No XOR, extraer directo
            int len = Character.getNumericValue(pinBlock.charAt(1));
            return pinBlock.substring(2, 2 + len);
            
        // ... otros formatos
    }
}
```

---

## 8. Módulo MAC - Códigos de Autenticación {#modulo-mac}

### 8.1 ISO 9797-1 Algorithm 1 (CBC-MAC)

**Clase:** `MACOperations.java`

```java
public static byte[] generateCBCMAC(byte[] data, byte[] key) throws Exception {
    // 1. Padding ISO 9797-1 Method 2
    byte[] paddedData = addISO9797Padding(data);
    
    // 2. CBC encrypt con IV=0
    Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
    IvParameterSpec iv = new IvParameterSpec(new byte[8]);
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DESede"), iv);
    
    byte[] encrypted = cipher.doFinal(paddedData);
    
    // 3. MAC = último bloque
    return Arrays.copyOfRange(encrypted, encrypted.length - 8, encrypted.length);
}
```

### 8.2 ISO 9797-1 Algorithm 3 (Retail MAC)

```java
public static byte[] generateRetailMAC(byte[] data, byte[] key) throws Exception {
    // 1. Padding
    byte[] paddedData = addISO9797Padding(data);
    
    // 2. Split key
    byte[] k1 = Arrays.copyOfRange(key, 0, 8);
    byte[] k2 = Arrays.copyOfRange(key, 8, 16);
    
    // 3. CBC-MAC con K1 (IV=0)
    Cipher cipher1 = Cipher.getInstance("DES/CBC/NoPadding", "BC");
    cipher1.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k1, "DES"), 
                 new IvParameterSpec(new byte[8]));
    byte[] encrypted = cipher1.doFinal(paddedData);
    byte[] lastBlock = Arrays.copyOfRange(encrypted, encrypted.length - 8, encrypted.length);
    
    // 4. Decrypt con K2
    Cipher cipher2 = Cipher.getInstance("DES/ECB/NoPadding", "BC");
    cipher2.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k2, "DES"));
    byte[] decrypted = cipher2.doFinal(lastBlock);
    
    // 5. Encrypt con K1
    cipher2.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k1, "DES"));
    byte[] mac = cipher2.doFinal(decrypted);
    
    return mac;  // 8 bytes
}
```

**ISO 9797-1 Padding Method 2:**
```java
private static byte[] addISO9797Padding(byte[] data) {
    int blockSize = 8;
    int paddingNeeded = blockSize - (data.length % blockSize);
    if (paddingNeeded == blockSize) paddingNeeded = 0;
    
    byte[] padded = new byte[data.length + paddingNeeded];
    System.arraycopy(data, 0, padded, 0, data.length);
    
    if (paddingNeeded > 0) {
        padded[data.length] = (byte) 0x80;  // Mandatory bit
        // Resto son 0x00
    }
    
    return padded;
}
```

### 8.3 CMAC (ISO 9797-1 Algorithm 5)

```java
public static byte[] generateCMAC(byte[] data, byte[] key, String algorithm) throws Exception {
    Mac mac = Mac.getInstance(algorithm + "CMAC", "BC");  // "AES" o "DESede"
    SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
    mac.init(keySpec);
    return mac.doFinal(data);
}
```

### 8.4 HMAC

```java
public static byte[] generateHMAC(byte[] data, byte[] key, String algorithm) throws Exception {
    Mac mac = Mac.getInstance("Hmac" + algorithm, "BC");  // "SHA256", "SHA384", "SHA512"
    SecretKeySpec keySpec = new SecretKeySpec(key, "Hmac" + algorithm);
    mac.init(keySpec);
    return mac.doFinal(data);
}
```

**Tamaños de Salida:**
- HMAC-SHA256: 32 bytes
- HMAC-SHA384: 48 bytes
- HMAC-SHA512: 64 bytes

### 8.5 Verificación MAC

```java
public static boolean verify(byte[] data, byte[] mac, byte[] key, String algorithm) throws Exception {
    byte[] calculatedMAC = generate(data, key, algorithm);
    return Arrays.equals(mac, calculatedMAC);
}
```

---

## 9. Módulo Signature - Firmas Digitales {#modulo-signature}

### 9.1 Algoritmos de Firma

**Clase:** `SignatureOperations.java`

**RSA Signatures:**
```java
// SHA256withRSA
public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA", "BC");
    signature.initSign(privateKey);
    signature.update(data);
    return signature.sign();
}

public static boolean verify(byte[] data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA", "BC");
    signature.initVerify(publicKey);
    signature.update(data);
    return signature.verify(signatureBytes);
}
```

**Variantes:**
- SHA1withRSA (Deprecated)
- SHA256withRSA (RS256)
- SHA384withRSA (RS384)
- SHA512withRSA (RS512)

**RSA-PSS:**
```java
Signature signature = Signature.getInstance("SHA256withRSA/PSS", "BC");

PSSParameterSpec pssSpec = new PSSParameterSpec(
    "SHA-256",                      // Hash algorithm
    "MGF1",                         // Mask generation function
    MGF1ParameterSpec.SHA256,       // MGF parameters
    32,                             // Salt length
    1                               // Trailer field
);
signature.setParameter(pssSpec);
```

**ECDSA:**
```java
// SHA256withECDSA (ES256)
Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
signature.initSign(privateKey);
signature.update(data);
byte[] signatureBytes = signature.sign();

// Nota: Firma en formato ASN.1 DER (r, s)
```

**Conversión ECDSA DER ↔ Raw (para JWT):**
```java
// ASN.1 DER to Raw R||S
public static byte[] convertDERtoRaw(byte[] derSignature, int keySize) throws Exception {
    ASN1Sequence seq = ASN1Sequence.getInstance(derSignature);
    BigInteger r = ((ASN1Integer) seq.getObjectAt(0)).getValue();
    BigInteger s = ((ASN1Integer) seq.getObjectAt(1)).getValue();
    
    int byteLength = (keySize + 7) / 8;
    byte[] rBytes = toFixedSizeArray(r, byteLength);
    byte[] sBytes = toFixedSizeArray(s, byteLength);
    
    byte[] raw = new byte[byteLength * 2];
    System.arraycopy(rBytes, 0, raw, 0, byteLength);
    System.arraycopy(sBytes, 0, raw, byteLength, byteLength);
    
    return raw;
}
```

**Ed25519:**
```java
Signature signature = Signature.getInstance("Ed25519", "BC");
signature.initSign(privateKey);
signature.update(data);
byte[] signatureBytes = signature.sign();  // 64 bytes

// Verificación
signature.initVerify(publicKey);
signature.update(data);
boolean valid = signature.verify(signatureBytes);
```

### 9.2 Paquetes de Validación

CryptoCarver incluye paquetes pre-configurados con:
- Mensaje de prueba
- Firma (varios algoritmos)
- Clave pública
- Algoritmo

**Paquetes Disponibles:**
1. Ed25519 Package
2. RSA-SHA256 Package
3. RSA-SHA384 Package
4. RSA-SHA512 Package
5. ECDSA-SHA256 Package (P-256)

**Uso:**
```
1. Seleccionar package
2. Click "Load Package"
3. Campos se cargan automáticamente
4. Click "Verify Signature"
5. Resultado: Valid/Invalid + detalles
```

---

## 10. Módulo ASN.1 - Parser {#modulo-asn1}

### 10.1 Parser Básico

**Clase:** `ASN1Parser.java`

```java
public static ASN1TreeNode parse(byte[] data) throws IOException {
    ByteArrayInputStream stream = new ByteArrayInputStream(data);
    return parseNode(stream, 0, data.length);
}

private static ASN1TreeNode parseNode(ByteArrayInputStream stream, int offset, int length) {
    // 1. Leer tag
    int tag = stream.read();
    boolean constructed = (tag & 0x20) != 0;
    
    // 2. Leer length
    int contentLength = readLength(stream);
    
    // 3. Leer content
    byte[] content = new byte[contentLength];
    stream.read(content);
    
    // 4. Crear nodo
    ASN1TreeNode node = new ASN1TreeNode(tag, contentLength, content, offset);
    
    // 5. Si constructed, parsear hijos
    if (constructed) {
        ByteArrayInputStream childStream = new ByteArrayInputStream(content);
        while (childStream.available() > 0) {
            ASN1TreeNode child = parseNode(childStream, ...);
            node.addChild(child);
        }
    }
    
    return node;
}
```

### 10.2 Length Encoding

```
Short form (< 128): 0x00 - 0x7F
Long form (>= 128):
  First byte: 0x80 | num_octets
  Following bytes: length (big-endian)
  
Ejemplo: Length = 256
  0x82 0x01 0x00
```

### 10.3 Schemas

**X.509 Certificate Schema:**
```
Certificate ::= SEQUENCE {
  tbsCertificate       TBSCertificate,
  signatureAlgorithm   AlgorithmIdentifier,
  signatureValue       BIT STRING
}
```

**PKCS#8 Private Key:**
```
PrivateKeyInfo ::= SEQUENCE {
  version               INTEGER,
  privateKeyAlgorithm   AlgorithmIdentifier,
  privateKey            OCTET STRING,
  attributes       [0]  IMPLICIT Attributes OPTIONAL
}
```

**PKCS#10 CSR:**
```
CertificationRequest ::= SEQUENCE {
  certificationRequestInfo  CertificationRequestInfo,
  signatureAlgorithm        AlgorithmIdentifier,
  signature                 BIT STRING
}
```

### 10.4 OID Registry

Algunos OIDs comunes:
```java
"1.2.840.113549.1.1.11" = sha256WithRSAEncryption
"1.2.840.10045.4.3.2"   = ecdsa-with-SHA256
"2.5.4.3"  = commonName (CN)
"2.5.4.10" = organizationName (O)
"2.5.29.15" = keyUsage
"2.5.29.19" = basicConstraints
```

---

## 11. Módulo CMS/PKCS#7 {#modulo-cms}

### 11.1 SignedData

**Clase:** `CMSOperations.java`

```java
public static byte[] generateSignedData(
    byte[] data,
    X509Certificate cert,
    PrivateKey privateKey,
    Map<String, String> signedAttributes,  // Opcional
    boolean detached
) throws Exception {
    
    // CMSTypedData
    CMSTypedData msg = new CMSProcessableByteArray(data);
    
    // Certificate store
    List<X509Certificate> certList = Arrays.asList(cert);
    Store certs = new JcaCertStore(certList);
    
    // Generator
    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
    
    // Content signer
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider("BC")
        .build(privateKey);
    
    // Add signer
    if (signedAttributes != null && !signedAttributes.isEmpty()) {
        AttributeTable attrTable = createAttributeTable(signedAttributes);
        gen.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .setSignedAttributeGenerator(new DefaultSignedAttributeTableGenerator(attrTable))
                .build(signer, cert));
    } else {
        gen.addSignerInfoGenerator(
            new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signer, cert));
    }
    
    // Add certificates
    gen.addCertificates(certs);
    
    // Generate (encapsulate = !detached)
    CMSSignedData signedData = gen.generate(msg, !detached);
    
    return signedData.getEncoded();
}
```

**Verificación:**
```java
public static boolean verifySignedData(byte[] pkcs7Data, X509Certificate cert) throws Exception {
    CMSSignedData signedData = new CMSSignedData(pkcs7Data);
    
    SignerInformationStore signers = signedData.getSignerInfos();
    
    for (SignerInformation signer : signers.getSigners()) {
        SignerInformationVerifier verifier = 
            new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC")
                .build(cert);
        
        if (!signer.verify(verifier)) {
            return false;
        }
    }
    
    return true;
}
```

### 11.2 EnvelopedData

```java
public static byte[] generateEnvelopedData(byte[] data, X509Certificate recipientCert) throws Exception {
    CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();
    
    // Add recipient
    gen.addRecipientInfoGenerator(
        new JceKeyTransRecipientInfoGenerator(recipientCert)
            .setProvider("BC")
    );
    
    // Output encryptor (AES-256-CBC)
    OutputEncryptor encryptor = 
        new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC)
            .setProvider("BC")
            .build();
    
    // Generate
    CMSEnvelopedData envelopedData = gen.generate(
        new CMSProcessableByteArray(data),
        encryptor
    );
    
    return envelopedData.getEncoded();
}
```

**Descifrado:**
```java
public static byte[] decryptEnvelopedData(byte[] envelopedData, PrivateKey privateKey) throws Exception {
    CMSEnvelopedData envData = new CMSEnvelopedData(envelopedData);
    
    RecipientInformationStore recipients = envData.getRecipientInfos();
    RecipientInformation recipient = recipients.getRecipients().iterator().next();
    
    Recipient jceRecipient = 
        new JceKeyTransEnvelopedRecipient(privateKey)
            .setProvider("BC");
    
    return recipient.getContent(jceRecipient);
}
```

---

## 12. Módulo JOSE - JWT/JWS/JWE {#modulo-jose}

### 12.1 JWT Generation

**Clase:** `JOSEController.java`

**Estructura JWT:**
```
[Header].[Payload].[Signature]
Cada parte Base64URL-encoded
```

```java
// 1. Construir header
Map<String, Object> header = new HashMap<>();
header.put("alg", "RS256");
header.put("typ", "JWT");

// 2. Construir payload
Map<String, Object> payload = new HashMap<>();
payload.put("sub", "user123");
payload.put("iat", System.currentTimeMillis() / 1000);
payload.put("exp", (System.currentTimeMillis() / 1000) + 3600);

// 3. Convertir a JSON y Base64URL encode
String headerB64 = base64UrlEncode(toJson(header));
String payloadB64 = base64UrlEncode(toJson(payload));

// 4. Signing input
String signingInput = headerB64 + "." + payloadB64;

// 5. Firmar
byte[] signatureBytes = sign(signingInput, privateKey, "SHA256withRSA");
String signatureB64 = base64UrlEncode(signatureBytes);

// 6. Construir JWT
String jwt = signingInput + "." + signatureB64;
```

### 12.2 JWT Validation

```java
// 1. Split
String[] parts = jwt.split("\\.");
if (parts.length != 3) throw new Exception("Invalid JWT");

// 2. Decode
byte[] headerBytes = base64UrlDecode(parts[0]);
byte[] payloadBytes = base64UrlDecode(parts[1]);
Map<String, Object> header = parseJson(headerBytes);
Map<String, Object> payload = parseJson(payloadBytes);

// 3. Obtener algoritmo
String alg = (String) header.get("alg");

// 4. Reconstituir signing input
String signingInput = parts[0] + "." + parts[1];

// 5. Decode signature
byte[] signatureBytes = base64UrlDecode(parts[2]);

// 6. Verificar
boolean valid = verify(signingInput, signatureBytes, publicKey, alg);

// 7. Validar claims
long exp = ((Number) payload.get("exp")).longValue();
if (System.currentTimeMillis() / 1000 > exp) {
    throw new Exception("Token expired");
}
```

### 12.3 Algoritmos Soportados

**HMAC:**
- HS256 (HMAC with SHA-256)
- HS384 (HMAC with SHA-384)
- HS512 (HMAC with SHA-512)

**RSA:**
- RS256 (RSA Signature with SHA-256)
- RS384 (RSA Signature with SHA-384)
- RS512 (RSA Signature with SHA-512)

**RSA-PSS:**
- PS256 (RSA-PSS with SHA-256)
- PS384 (RSA-PSS with SHA-384)
- PS512 (RSA-PSS with SHA-512)

**ECDSA:**
- ES256 (ECDSA with P-256 and SHA-256)
- ES384 (ECDSA with P-384 and SHA-384)
- ES512 (ECDSA with P-521 and SHA-512)

**EdDSA:**
- Ed25519

### 12.4 Key Formats

**PEM:**
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBg...
-----END PUBLIC KEY-----
```

**JWK (RSA):**
```json
{
  "kty": "RSA",
  "n": "0vx7agoebGcQ...",
  "e": "AQAB"
}
```

**JWK (EC):**
```json
{
  "kty": "EC",
  "crv": "P-256",
  "x": "f83OJ3D2xF1Bg8...",
  "y": "x_FEzRu9m36HLN..."
}
```

---

## 13. Vectores de Verificación {#vectores}

### 13.1 CVV Test Vectors

```
Test Case 1:
CVK A: 0123456789ABCDEF
CVK B: FEDCBA9876543210
PAN: 4123450000001234
Expiry: 2512
Service Code: 101
Expected CVV: [según implementación]
```

### 13.2 EMV ARQC/ARPC

```
Session Key: 0123456789ABCDEFFEDCBA9876543210
ARQC: 1234567890ABCDEF
ARC: 00
Expected ARPC (Method 1): 3B2857C78D2464A8
```

### 13.3 PIN Block ISO-0

```
PIN: 1234
PAN: 4123450000001234
Expected PIN Block: 0412XXXXXXXXXXXX (depends on XOR)
```

### 13.4 MAC ISO 9797-1 Alg 3

```
Key: 0123456789ABCDEFFEDCBA9876543210
Data: "Hello World" (hex)
Expected MAC: [8 bytes hex]
```

---

## 14. Características de UI

### 14.1 History y Sessions

- Historial de operaciones
- Guardar/cargar sesiones
- Export/Import de configuraciones

### 14.2 Conversión de Datos

**DataConverter integrado:**
- Hex ↔ Base64
- Hex ↔ Text (UTF-8)
- Binary ↔ Hex
- Hex Dump view

### 14.3 Clipboard Integration

- Copy resultado con un click
- Paste desde clipboard
- Múltiples formatos

---

## Apéndice A: Estándares de Referencia

- **ISO 9564**: PIN Management
- **ISO 9797-1**: MAC algorithms
- **ISO/IEC 7816-4**: Smart card padding
- **ANSI X9.143**: TR-31 Key Blocks
- **RFC 5280**: X.509 Certificates
- **RFC 5652**: CMS (PKCS#7)
- **RFC 7519**: JWT
- **EMV 4.3 Book 2**: ICC and Terminal Specifications
- **NIST SP 800-38D**: GCM Mode
- **NIST FIPS 198-1**: HMAC

---

## Apéndice B: Provider Dependencies

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78.1</version>
</dependency>

<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.17.1</version>
</dependency>
```

---

**Fin de la Guía Técnica en Español**
