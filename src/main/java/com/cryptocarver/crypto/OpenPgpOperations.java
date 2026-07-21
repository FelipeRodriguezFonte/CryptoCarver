package com.cryptocarver.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPMarker;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Small OpenPGP toolkit for laboratory interoperability with GnuPG.
 *
 * <p>All methods consume and produce ASCII-armored material. The caller owns
 * passphrase lifecycle and should clear its character arrays after use.</p>
 */
public final class OpenPgpOperations {
    private static final int RSA_BITS = 3072;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private OpenPgpOperations() {
    }

    public record KeyPairMaterial(String publicKeyArmored, String secretKeyArmored,
                                  String keyId, String fingerprint) {
    }

    public record DecryptionResult(byte[] plaintext, String recipientKeyId,
                                   boolean integrityProtected) {
    }

    public record VerificationResult(boolean valid, String signerKeyId, String fingerprint) {
    }

    public record SignedMessageVerificationResult(boolean valid, byte[] content,
                                                  String signerKeyId, String fingerprint) {
    }

    /** Public, non-secret metadata suitable for displaying an imported OpenPGP key. */
    public record KeyInspection(boolean secretKeyMaterial, String keyId, String fingerprint,
                                int algorithm, int bitStrength, List<String> userIds,
                                boolean encryptionCapable, boolean signingCapable) {
    }

    /**
     * Public metadata of a detached OpenPGP signature. Parsing this object does
     * not verify the signature and does not establish OpenPGP Web-of-Trust.
     */
    public record DetachedSignatureInspection(String signerKeyId, int version,
                                              String signatureType, String keyAlgorithm,
                                              String hashAlgorithm, Instant creationTime) {
    }

    public static KeyPairMaterial generateRsaKeyPair(String userId, char[] passphrase) throws Exception {
        requireText(userId, "User ID");
        requirePassphrase(passphrase);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        generator.initialize(RSA_BITS, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();
        PGPKeyPair pgpPair = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, pair, Date.from(Instant.now()));
        PGPDigestCalculator digestCalculator = new org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder()
                .build().get(HashAlgorithmTags.SHA1);
        PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                pgpPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider("BC");
        PBESecretKeyEncryptor secretKeyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.AES_256, digestCalculator).setProvider("BC").build(passphrase);
        PGPKeyRingGenerator ringGenerator = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION, pgpPair, userId, digestCalculator,
                null, null, signerBuilder, secretKeyEncryptor);

        PGPPublicKeyRing publicRing = ringGenerator.generatePublicKeyRing();
        PGPSecretKeyRing secretRing = ringGenerator.generateSecretKeyRing();
        PGPPublicKey key = publicRing.getPublicKey();
        return new KeyPairMaterial(armor(publicRing), armor(secretRing), keyId(key), fingerprint(key));
    }

    public static String encrypt(String plaintext, String recipientPublicKeyArmored) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext is required");
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), recipientPublicKeyArmored);
    }

    public static String encrypt(byte[] plaintext, String recipientPublicKeyArmored) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext is required");
        }
        PGPPublicKey recipient = findEncryptionKey(readPublicKeys(recipientPublicKeyArmored));
        if (recipient == null) {
            throw new IllegalArgumentException("No encryption-capable public key was found");
        }

        PGPEncryptedDataGenerator encrypted = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider("BC"));
        encrypted.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(recipient).setProvider("BC"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            try (OutputStream encryptedOutput = encrypted.open(armor, new byte[4096])) {
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream literalOutput = literal.open(encryptedOutput, PGPLiteralData.BINARY,
                        PGPLiteralData.CONSOLE, plaintext.length, new Date())) {
                    literalOutput.write(plaintext);
                }
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    public static DecryptionResult decrypt(String encryptedArmored, String secretKeyArmored,
                                           char[] passphrase) throws Exception {
        requireText(encryptedArmored, "Encrypted OpenPGP message");
        requirePassphrase(passphrase);
        PGPSecretKeyRingCollection secretKeys = readSecretKeys(secretKeyArmored);
        PGPEncryptedDataList encryptedData = encryptedDataList(encryptedArmored);

        Iterator<?> encryptedObjects = encryptedData.getEncryptedDataObjects();
        while (encryptedObjects.hasNext()) {
            Object object = encryptedObjects.next();
            if (!(object instanceof PGPPublicKeyEncryptedData candidate)) {
                continue;
            }
            PGPSecretKey secretKey = secretKeys.getSecretKey(candidate.getKeyID());
            if (secretKey == null) {
                continue;
            }
            try {
                PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(
                        new org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder().build())
                        .setProvider("BC").build(passphrase);
                org.bouncycastle.openpgp.PGPPrivateKey privateKey = secretKey.extractPrivateKey(decryptor);
                PublicKeyDataDecryptorFactory dataDecryptor = new JcePublicKeyDataDecryptorFactoryBuilder()
                        .setProvider("BC").build(privateKey);
                try (InputStream clear = candidate.getDataStream(dataDecryptor)) {
                    byte[] plaintext = readLiteralData(clear);
                    boolean integrity = !candidate.isIntegrityProtected() || candidate.verify();
                    if (!integrity) {
                        throw new PGPException("OpenPGP integrity check failed");
                    }
                    return new DecryptionResult(plaintext, keyId(secretKey.getPublicKey()), candidate.isIntegrityProtected());
                }
            } catch (PGPException wrongPassphraseOrKey) {
                // A secret ring may carry several keys. Continue until one decrypts.
            }
        }
        throw new PGPException("No OpenPGP recipient could be decrypted with the supplied secret key and passphrase");
    }

    public static String signDetached(String data, String secretKeyArmored, char[] passphrase) throws Exception {
        return signDetached(data == null ? null : data.getBytes(StandardCharsets.UTF_8), secretKeyArmored, passphrase);
    }

    public static String signDetached(byte[] data, String secretKeyArmored, char[] passphrase) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Data to sign is required");
        }
        requirePassphrase(passphrase);
        PGPSecretKey signingKey = findSigningKey(readSecretKeys(secretKeyArmored));
        if (signingKey == null) {
            throw new IllegalArgumentException("No signing-capable secret key was found");
        }
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(
                new org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder().build())
                .setProvider("BC").build(passphrase);
        org.bouncycastle.openpgp.PGPPrivateKey privateKey = signingKey.extractPrivateKey(decryptor);
        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(signingKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
                        .setProvider("BC"));
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
        signatureGenerator.update(data);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            signatureGenerator.generate().encode(armor);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    public static VerificationResult verifyDetached(String data, String signatureArmored,
                                                    String publicKeyArmored) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Signed data is required");
        }
        return verifyDetached(data.getBytes(StandardCharsets.UTF_8), signatureArmored, publicKeyArmored);
    }

    /** Verifies a detached signature over the exact supplied bytes. */
    public static VerificationResult verifyDetached(byte[] data, String signatureArmored,
                                                    String publicKeyArmored) throws Exception {
        if (data == null) {
            throw new IllegalArgumentException("Signed data is required");
        }
        requireText(signatureArmored, "Detached signature");
        PGPPublicKeyRingCollection publicKeys = readPublicKeys(publicKeyArmored);
        PGPSignature signature = readSignature(signatureArmored);
        PGPPublicKey signingKey = publicKeys.getPublicKey(signature.getKeyID());
        if (signingKey == null) {
            throw new IllegalArgumentException("The signing key is not present in the supplied public key ring");
        }
        signature.init(new org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider()
                .setProvider("BC"), signingKey);
        signature.update(data);
        return new VerificationResult(signature.verify(), keyId(signingKey), fingerprint(signingKey));
    }

    /** Parses a detached signature without requiring the signer public key or signed data. */
    public static DetachedSignatureInspection inspectDetachedSignature(String signatureArmored) throws Exception {
        requireText(signatureArmored, "Detached signature");
        PGPSignature signature = readSignature(signatureArmored);
        Date created = signature.getCreationTime();
        return new DetachedSignatureInspection(keyId(signature.getKeyID()), signature.getVersion(),
                signatureTypeName(signature.getSignatureType()), publicKeyAlgorithmName(signature.getKeyAlgorithm()),
                hashAlgorithmName(signature.getHashAlgorithm()), created == null ? null : created.toInstant());
    }

    /** Creates an ASCII-armored OpenPGP signed message with its content attached. */
    public static String signAttached(byte[] data, String secretKeyArmored, char[] passphrase) throws Exception {
        if (data == null) throw new IllegalArgumentException("Data to sign is required");
        PGPSecretKey signingKey = requireSigningKey(secretKeyArmored);
        PGPPrivateKey privateKey = extractPrivateKey(signingKey, passphrase);
        PGPSignatureGenerator signature = signatureGenerator(signingKey, privateKey, PGPSignature.BINARY_DOCUMENT);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            try (BCPGOutputStream packets = new BCPGOutputStream(armor)) {
                signature.generateOnePassVersion(false).encode(packets);
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream literalOutput = literal.open(packets, PGPLiteralData.BINARY,
                        PGPLiteralData.CONSOLE, data.length, new Date())) {
                    literalOutput.write(data);
                    signature.update(data);
                }
                signature.generate().encode(packets);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Verifies an attached signed message and returns its original bytes for a valid or invalid result. */
    public static SignedMessageVerificationResult verifyAttached(String signedMessageArmored,
                                                                  String publicKeyArmored) throws Exception {
        requireText(signedMessageArmored, "OpenPGP signed message");
        PGPPublicKeyRingCollection publicKeys = readPublicKeys(publicKeyArmored);
        PGPObjectFactory factory = objectFactory(signedMessageArmored);
        Object object = nextNonMarker(factory);
        if (object instanceof PGPCompressedData compressed) {
            factory = new PGPObjectFactory(compressed.getDataStream(), new JcaKeyFingerprintCalculator());
            object = nextNonMarker(factory);
        }
        if (!(object instanceof PGPOnePassSignatureList onePassSignatures) || onePassSignatures.isEmpty()) {
            throw new PGPException("Input does not contain an OpenPGP attached signature");
        }
        PGPOnePassSignature onePass = onePassSignatures.get(0);
        PGPPublicKey signer = publicKeys.getPublicKey(onePass.getKeyID());
        if (signer == null) throw new IllegalArgumentException("The signing key is not present in the supplied public key ring");
        onePass.init(new org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider().setProvider("BC"), signer);

        Object literalObject = factory.nextObject();
        if (!(literalObject instanceof PGPLiteralData literal)) {
            throw new PGPException("OpenPGP signed message does not contain literal data");
        }
        byte[] content;
        try (InputStream literalInput = literal.getInputStream()) {
            content = readLimited(literalInput);
        }
        onePass.update(content);
        Object finalObject = factory.nextObject();
        if (!(finalObject instanceof PGPSignatureList signatures) || signatures.isEmpty()) {
            throw new PGPException("OpenPGP signed message does not contain a final signature packet");
        }
        return new SignedMessageVerificationResult(onePass.verify(signatures.get(0)), content, keyId(signer), fingerprint(signer));
    }

    /** Creates a readable RFC 4880 clear-signed text message. */
    public static String clearSign(String text, String secretKeyArmored, char[] passphrase) throws Exception {
        requireText(text, "Text to sign");
        PGPSecretKey signingKey = requireSigningKey(secretKeyArmored);
        PGPPrivateKey privateKey = extractPrivateKey(signingKey, passphrase);
        PGPSignatureGenerator signature = signatureGenerator(signingKey, privateKey, PGPSignature.CANONICAL_TEXT_DOCUMENT);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int count = normalized.endsWith("\n") ? lines.length - 1 : lines.length;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            armor.beginClearText(HashAlgorithmTags.SHA256);
            for (int index = 0; index < count; index++) {
                byte[] line = lines[index].getBytes(StandardCharsets.UTF_8);
                armor.write(line);
                armor.write('\n');
                signature.update(line);
                signature.update((byte) '\r');
                signature.update((byte) '\n');
            }
            armor.endClearText();
            try (BCPGOutputStream packets = new BCPGOutputStream(armor)) {
                signature.generate().encode(packets);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Verifies a clear-signed message and returns its displayed clear text. */
    public static SignedMessageVerificationResult verifyClearSigned(String clearSignedArmored,
                                                                     String publicKeyArmored) throws Exception {
        requireText(clearSignedArmored, "Clear-signed message");
        int headerEnd = clearSignedArmored.indexOf("\n\n");
        if (headerEnd < 0) headerEnd = clearSignedArmored.indexOf("\r\n\r\n");
        if (headerEnd < 0) throw new PGPException("Invalid OpenPGP clear-signed message header");
        int clearStart = headerEnd + (clearSignedArmored.startsWith("\r\n", headerEnd) ? 4 : 2);
        int signatureStart = clearSignedArmored.indexOf("-----BEGIN PGP SIGNATURE-----", clearStart);
        if (signatureStart < 0) throw new PGPException("OpenPGP clear-signed message has no signature block");
        String displayed = clearSignedArmored.substring(clearStart, signatureStart).replaceFirst("(?:\\r?\\n)$", "");
        String canonical = displayed.replace("\r\n", "\n").replace('\r', '\n');
        PGPSignature signature = readSignature(clearSignedArmored.substring(signatureStart));
        PGPPublicKeyRingCollection publicKeys = readPublicKeys(publicKeyArmored);
        PGPPublicKey signer = publicKeys.getPublicKey(signature.getKeyID());
        if (signer == null) throw new IllegalArgumentException("The signing key is not present in the supplied public key ring");
        signature.init(new org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider().setProvider("BC"), signer);
        for (String line : canonical.split("\n", -1)) {
            String unescaped = line.startsWith("- ") ? line.substring(2) : line;
            signature.update(unescaped.getBytes(StandardCharsets.UTF_8));
            signature.update((byte) '\r');
            signature.update((byte) '\n');
        }
        return new SignedMessageVerificationResult(signature.verify(), displayed.getBytes(StandardCharsets.UTF_8), keyId(signer), fingerprint(signer));
    }

    /**
     * Reads only public metadata from an ASCII-armored public or secret key ring. It never
     * decrypts or exports the private-key portion of secret material.
     */
    public static KeyInspection inspectKey(String armored) throws Exception {
        requireText(armored, "OpenPGP key");
        boolean secret = armored.contains("BEGIN PGP PRIVATE KEY BLOCK");
        PGPPublicKey primary;
        List<String> userIds = new ArrayList<>();
        boolean canEncrypt = false;
        boolean canSign = false;
        if (secret) {
            PGPSecretKeyRingCollection rings = readSecretKeys(armored);
            Iterator<PGPSecretKeyRing> ringIterator = rings.getKeyRings();
            if (!ringIterator.hasNext()) {
                throw new PGPException("OpenPGP secret key ring is empty");
            }
            PGPSecretKeyRing ring = ringIterator.next();
            primary = ring.getPublicKey();
            Iterator<PGPSecretKey> keys = ring.getSecretKeys();
            while (keys.hasNext()) {
                PGPSecretKey key = keys.next();
                canEncrypt |= key.getPublicKey().isEncryptionKey();
                canSign |= key.isSigningKey();
            }
        } else {
            PGPPublicKeyRingCollection rings = readPublicKeys(armored);
            Iterator<PGPPublicKeyRing> ringIterator = rings.getKeyRings();
            if (!ringIterator.hasNext()) {
                throw new PGPException("OpenPGP public key ring is empty");
            }
            PGPPublicKeyRing ring = ringIterator.next();
            primary = ring.getPublicKey();
            Iterator<PGPPublicKey> keys = ring.getPublicKeys();
            while (keys.hasNext()) {
                PGPPublicKey key = keys.next();
                canEncrypt |= key.isEncryptionKey();
                canSign |= key.isMasterKey();
            }
        }
        Iterator<String> identities = primary.getUserIDs();
        while (identities.hasNext()) {
            userIds.add(identities.next());
        }
        return new KeyInspection(secret, keyId(primary), fingerprint(primary), primary.getAlgorithm(),
                primary.getBitStrength(), List.copyOf(userIds), canEncrypt, canSign);
    }

    private static PGPEncryptedDataList encryptedDataList(String armored) throws IOException, PGPException {
        PGPObjectFactory factory = objectFactory(armored);
        Object first = factory.nextObject();
        if (first instanceof PGPMarker) {
            first = factory.nextObject();
        }
        if (first instanceof PGPEncryptedDataList list) {
            return list;
        }
        Object next = factory.nextObject();
        if (next instanceof PGPEncryptedDataList list) {
            return list;
        }
        throw new PGPException("Input does not contain OpenPGP encrypted data");
    }

    private static byte[] readLiteralData(InputStream clear) throws IOException, PGPException {
        PGPObjectFactory factory = new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());
        Object message = factory.nextObject();
        if (message instanceof PGPCompressedData compressed) {
            factory = new PGPObjectFactory(compressed.getDataStream(), new JcaKeyFingerprintCalculator());
            message = factory.nextObject();
        }
        if (!(message instanceof PGPLiteralData literal)) {
            throw new PGPException("OpenPGP message does not contain literal data");
        }
        try (InputStream literalInput = literal.getInputStream()) {
            return readLimited(literalInput);
        }
    }

    private static PGPSignature readSignature(String armored) throws IOException, PGPException {
        PGPObjectFactory factory = objectFactory(armored);
        Object object = factory.nextObject();
        if (object instanceof PGPCompressedData compressed) {
            factory = new PGPObjectFactory(compressed.getDataStream(), new JcaKeyFingerprintCalculator());
            object = factory.nextObject();
        }
        if (object instanceof PGPSignatureList signatures && !signatures.isEmpty()) {
            return signatures.get(0);
        }
        throw new PGPException("Input does not contain an OpenPGP detached signature");
    }

    private static Object nextNonMarker(PGPObjectFactory factory) throws IOException {
        Object object = factory.nextObject();
        return object instanceof PGPMarker ? factory.nextObject() : object;
    }

    private static PGPSecretKey requireSigningKey(String secretKeyArmored) throws IOException, PGPException {
        PGPSecretKey signingKey = findSigningKey(readSecretKeys(secretKeyArmored));
        if (signingKey == null) throw new IllegalArgumentException("No signing-capable secret key was found");
        return signingKey;
    }

    private static PGPPrivateKey extractPrivateKey(PGPSecretKey signingKey, char[] passphrase) throws PGPException {
        requirePassphrase(passphrase);
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(
                new org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder().build())
                .setProvider("BC").build(passphrase);
        return signingKey.extractPrivateKey(decryptor);
    }

    private static PGPSignatureGenerator signatureGenerator(PGPSecretKey signingKey, PGPPrivateKey privateKey,
                                                             int signatureType) throws PGPException {
        PGPSignatureGenerator generator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(signingKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
                        .setProvider("BC"));
        generator.init(signatureType, privateKey);
        return generator;
    }

    private static PGPPublicKeyRingCollection readPublicKeys(String armored) throws IOException, PGPException {
        requireText(armored, "Public key");
        try (InputStream input = PGPUtil.getDecoderStream(new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            return new PGPPublicKeyRingCollection(input, new JcaKeyFingerprintCalculator());
        }
    }

    private static PGPSecretKeyRingCollection readSecretKeys(String armored) throws IOException, PGPException {
        requireText(armored, "Secret key");
        try (InputStream input = PGPUtil.getDecoderStream(new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            return new PGPSecretKeyRingCollection(input, new JcaKeyFingerprintCalculator());
        }
    }

    private static PGPObjectFactory objectFactory(String armored) throws IOException {
        InputStream decoder = PGPUtil.getDecoderStream(new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)));
        return new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
    }

    private static PGPPublicKey findEncryptionKey(PGPPublicKeyRingCollection rings) {
        Iterator<PGPPublicKeyRing> ringIterator = rings.getKeyRings();
        while (ringIterator.hasNext()) {
            Iterator<PGPPublicKey> keyIterator = ringIterator.next().getPublicKeys();
            while (keyIterator.hasNext()) {
                PGPPublicKey key = keyIterator.next();
                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }
        return null;
    }

    private static PGPSecretKey findSigningKey(PGPSecretKeyRingCollection rings) {
        Iterator<PGPSecretKeyRing> ringIterator = rings.getKeyRings();
        while (ringIterator.hasNext()) {
            Iterator<PGPSecretKey> keyIterator = ringIterator.next().getSecretKeys();
            while (keyIterator.hasNext()) {
                PGPSecretKey key = keyIterator.next();
                if (key.isSigningKey()) {
                    return key;
                }
            }
        }
        return null;
    }

    private static String armor(PGPPublicKeyRing ring) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            ring.encode(armor);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String armor(PGPSecretKeyRing ring) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(bytes)) {
            ring.encode(armor);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_MESSAGE_BYTES) {
                throw new IOException("OpenPGP plaintext exceeds the laboratory limit of " + MAX_MESSAGE_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String keyId(PGPPublicKey key) {
        return String.format("%016X", key.getKeyID());
    }

    private static String keyId(long keyId) {
        return String.format("%016X", keyId);
    }

    private static String fingerprint(PGPPublicKey key) {
        StringBuilder output = new StringBuilder();
        for (byte value : key.getFingerprint()) {
            output.append(String.format("%02X", value));
        }
        return output.toString();
    }

    private static String signatureTypeName(int type) {
        return switch (type) {
            case PGPSignature.BINARY_DOCUMENT -> "Binary document";
            case PGPSignature.CANONICAL_TEXT_DOCUMENT -> "Canonical text document";
            case PGPSignature.STAND_ALONE -> "Standalone";
            case PGPSignature.TIMESTAMP -> "Timestamp";
            case PGPSignature.DIRECT_KEY -> "Direct-key";
            case PGPSignature.KEY_REVOCATION -> "Key revocation";
            case PGPSignature.SUBKEY_BINDING -> "Subkey binding";
            case PGPSignature.PRIMARYKEY_BINDING -> "Primary-key binding";
            default -> "OpenPGP signature type " + type;
        };
    }

    private static String hashAlgorithmName(int algorithm) {
        return switch (algorithm) {
            case HashAlgorithmTags.SHA1 -> "SHA-1";
            case HashAlgorithmTags.SHA224 -> "SHA-224";
            case HashAlgorithmTags.SHA256 -> "SHA-256";
            case HashAlgorithmTags.SHA384 -> "SHA-384";
            case HashAlgorithmTags.SHA512 -> "SHA-512";
            case HashAlgorithmTags.SHA3_256 -> "SHA3-256";
            case HashAlgorithmTags.SHA3_512 -> "SHA3-512";
            case HashAlgorithmTags.RIPEMD160 -> "RIPEMD-160";
            default -> "OpenPGP hash algorithm " + algorithm;
        };
    }

    private static String publicKeyAlgorithmName(int algorithm) {
        return switch (algorithm) {
            case PublicKeyAlgorithmTags.RSA_GENERAL -> "RSA (general)";
            case PublicKeyAlgorithmTags.RSA_SIGN -> "RSA (signing)";
            case PublicKeyAlgorithmTags.RSA_ENCRYPT -> "RSA (encryption)";
            case PublicKeyAlgorithmTags.DSA -> "DSA";
            case PublicKeyAlgorithmTags.ECDSA -> "ECDSA";
            case PublicKeyAlgorithmTags.EDDSA -> "EdDSA";
            case PublicKeyAlgorithmTags.Ed25519 -> "Ed25519";
            case PublicKeyAlgorithmTags.Ed448 -> "Ed448";
            default -> "OpenPGP public-key algorithm " + algorithm;
        };
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("A key passphrase is required");
        }
    }
}
