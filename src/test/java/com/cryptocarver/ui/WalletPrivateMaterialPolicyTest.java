package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.upokecenter.cbor.CBORObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Direct Wallet-only COSE JSON checks, including malformed inputs and the bstr boundary. */
class WalletPrivateMaterialPolicyTest {

    @Test
    void nullEmptyAndInvalidJsonAreNotPrivate() {
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(null));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(""));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("   "));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("not json"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("{\"1\":4,\"-1\":\"secret\"} trailing"));
        // Lenient Gson object-key syntax with COSE integer labels is not valid JSON.
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("{1:4,-1:\"secret\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("{\"1\":4, -4:\"secret\"}"));
    }

    @Test
    void emptyContainersAndObjectsWithoutCoseKtyAreNotPrivate() {
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("{}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("[]"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("null"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson("{\"-4\":\"private-without-kty\"}"));
    }

    @Test
    void findsPrivateCoseMaterialAtNestedObjectAndArrayDepth() {
        String privateEc2 = "{\"1\":2,\"-4\":\"invented-private-d\"}";
        String privateSymmetric = "{\"1\":4,\"-1\":\"invented-private-k\"}";
        assertTrue(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(privateEc2));
        assertTrue(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(privateSymmetric));

        String nested = privateEc2;
        for (int i = 0; i < 256; i++) nested = "[ {\"layer\": " + nested + " } ]";
        assertTrue(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(nested),
                "recursive arrays and objects should be searched to the supported depth");
    }

    @Test
    void publicCoseKeyDoesNotCountAsPrivate() {
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":2,\"-1\":1,\"-2\":\"x\",\"-3\":\"y\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":1,\"crv\":\"P-256\",\"x\":\"x\",\"y\":\"y\"}"));
    }

    @Test
    void coseKtyMustBeNumericFourExactlyAndMalformedValuesDoNotThrow() {
        assertTrue(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":4,\"-1\":\"invented-k\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":4.5,\"-1\":\"not-a-cose-k\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":null,\"-1\":\"malformed\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":[],\"-1\":\"malformed\"}"));
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(
                "{\"1\":{},\"-1\":\"malformed\"}"));
    }

    @Test
    void jsonRenderingOfRealTag24BstrShowsTheJsonOnlyDetectionBoundary() throws Exception {
        // Construct actual tag-24 encoded CBOR containing a private COSE key. CborInspector's JSON
        // rendering gives a base64url string, not a nested JSON map.
        var generator = javax.crypto.KeyGenerator.getInstance("AES"); generator.init(256);
        CBORObject coseKey = CBORObject.NewMap()
                .Add(CBORObject.FromObject(1), CBORObject.FromObject(4))
                .Add(CBORObject.FromObject(-1), CBORObject.FromObject(generator.generateKey().getEncoded()));
        byte[] tag24 = CborInspector.wrapAsEncodedCbor(coseKey.EncodeToBytes());
        String json = CborInspector.toJson(tag24);
        assertFalse(WalletPrivateMaterialPolicy.containsPrivateCoseKeyJson(json), json);
    }
}
