package com.cryptoforge.crypto.hsm;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.KeyStore;
import java.security.PrivateKey;
import static org.junit.jupiter.api.Assertions.fail;

public class Pkcs11SessionEncapsulationTest {

    @Test
    public void testNoPublicApiExposesSensitiveTypes() {
        Method[] methods = Pkcs11Session.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers())) {
                Class<?> returnType = method.getReturnType();
                if (returnType.equals(PrivateKey.class)) {
                    fail("Pkcs11Session exposes PrivateKey in method: " + method.getName());
                }
                if (returnType.equals(KeyStore.class)) {
                    fail("Pkcs11Session exposes KeyStore in method: " + method.getName());
                }
                // Although byte[] can be returned for signatures (like signPades) or decrypted data,
                // we should ensure no method named something like "getKeyBytes" or "exportKey" exists returning byte[].
                if (returnType.equals(byte[].class)) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("key") && (name.contains("export") || name.contains("get"))) {
                        fail("Pkcs11Session might expose key bytes in method: " + method.getName());
                    }
                }
            }
        }
    }

    @Test
    public void testUpdateCertificateChainEncapsulation() throws NoSuchMethodException {
        Method method = Pkcs11Session.class.getMethod("updateCertificateChain", String.class, java.security.cert.Certificate[].class);

        // Ensure the method is public so it can be used, but returns void so it doesn't leak anything
        org.junit.jupiter.api.Assertions.assertTrue(Modifier.isPublic(method.getModifiers()), "updateCertificateChain must be public");
        org.junit.jupiter.api.Assertions.assertEquals(void.class, method.getReturnType(), "updateCertificateChain must return void to maintain encapsulation");
    }
}
