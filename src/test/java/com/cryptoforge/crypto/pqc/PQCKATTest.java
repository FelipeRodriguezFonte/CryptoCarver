package com.cryptoforge.crypto.pqc;

import com.cryptoforge.util.DataConverter;
import com.cryptoforge.crypto.PostQuantumOperations;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPublicKeyParameters;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusParameters;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusPublicKeyParameters;
import org.bouncycastle.pqc.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.pqc.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PQCKATTest {

    @BeforeAll
    static void setup() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void testKyber512KAT() throws Exception {
        Map<String, String> kat = loadFirstKat("pqc/kat/kyber512.rsp", true);

        byte[] pk = DataConverter.hexToBytes(kat.get("pk"));
        byte[] sk = DataConverter.hexToBytes(kat.get("sk"));
        byte[] ct = DataConverter.hexToBytes(kat.get("ct"));
        byte[] ss = DataConverter.hexToBytes(kat.get("ss"));

        KyberPublicKeyParameters pubParams = new KyberPublicKeyParameters(KyberParameters.kyber512, pk);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams);

        KeyFactory kf = KeyFactory.getInstance("Kyber", "BCPQC");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(spki.getEncoded()));

        KyberPrivateKeyParameters privParams = new KyberPrivateKeyParameters(KyberParameters.kyber512, sk);
        PrivateKeyInfo pki = PrivateKeyInfoFactory.createPrivateKeyInfo(privParams);
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pki.getEncoded()));

        byte[] recoveredSecret = PostQuantumOperations.decapsulate(privateKey, ct, "ML-KEM-512");
        assertArrayEquals(ss, recoveredSecret, "Decapsulated KAT ciphertext should match KAT shared secret");
    }

    @Test
    void testDilithium2KAT() throws Exception {
        Map<String, String> kat = loadFirstKat("pqc/kat/PQCsignKAT_Dilithium2.rsp", false);

        byte[] pk = DataConverter.hexToBytes(kat.get("pk"));
        byte[] sk = DataConverter.hexToBytes(kat.get("sk"));
        byte[] msg = DataConverter.hexToBytes(kat.get("msg"));
        byte[] sm = DataConverter.hexToBytes(kat.get("sm"));

        int sigLen = sm.length - msg.length;
        byte[] sig = Arrays.copyOfRange(sm, 0, sigLen);

        DilithiumPublicKeyParameters pubParams = new DilithiumPublicKeyParameters(DilithiumParameters.dilithium2, pk);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams);
        KeyFactory kf = KeyFactory.getInstance("Dilithium", "BCPQC");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(spki.getEncoded()));

        DilithiumPrivateKeyParameters privParams = new DilithiumPrivateKeyParameters(DilithiumParameters.dilithium2, sk, pubParams);
        PrivateKeyInfo pki = PrivateKeyInfoFactory.createPrivateKeyInfo(privParams);
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pki.getEncoded()));

        boolean isValid = PostQuantumOperations.verify(publicKey, msg, sig, "ML-DSA-44");
        assertTrue(isValid, "KAT signature should be successfully verified");
    }

    @Test
    void testSphincsPlusKAT() throws Exception {
        Map<String, String> kat = loadFirstKat("pqc/kat/subset_sha2-128f-simple.rsp", false);

        byte[] pk = DataConverter.hexToBytes(kat.get("pk"));
        byte[] sk = DataConverter.hexToBytes(kat.get("sk"));
        byte[] msg = DataConverter.hexToBytes(kat.get("msg"));
        byte[] sm = DataConverter.hexToBytes(kat.get("sm"));

        int sigLen = sm.length - msg.length;
        byte[] sig = Arrays.copyOfRange(sm, 0, sigLen);

        SPHINCSPlusPublicKeyParameters pubParams = new SPHINCSPlusPublicKeyParameters(SPHINCSPlusParameters.sha2_128f, pk);
        SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams);
        KeyFactory kf = KeyFactory.getInstance("SPHINCSPlus", "BCPQC");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(spki.getEncoded()));

        SPHINCSPlusPrivateKeyParameters privParams = new SPHINCSPlusPrivateKeyParameters(SPHINCSPlusParameters.sha2_128f, sk);
        PrivateKeyInfo pki = PrivateKeyInfoFactory.createPrivateKeyInfo(privParams);
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pki.getEncoded()));

        boolean isValid = PostQuantumOperations.verify(publicKey, msg, sig, "SLH-DSA-SHA2-128f");
        assertTrue(isValid, "KAT signature should be successfully verified");
    }

    private Map<String, String> loadFirstKat(String resourcePath, boolean isKem) throws Exception {
        Map<String, String> values = new HashMap<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + resourcePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    values.put(parts[0].trim(), parts[1].trim());
                }
                if (isKem && values.containsKey("pk") && values.containsKey("sk") && values.containsKey("ct") && values.containsKey("ss")) {
                    break;
                } else if (!isKem && values.containsKey("pk") && values.containsKey("sk") && values.containsKey("msg") && values.containsKey("sm")) {
                    break;
                }
            }
        }
        return values;
    }
}
