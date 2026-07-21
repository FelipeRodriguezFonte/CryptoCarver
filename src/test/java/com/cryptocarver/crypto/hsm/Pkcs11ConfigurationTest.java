package com.cryptocarver.crypto.hsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Test;

class Pkcs11ConfigurationTest {

    @Test
    void rendersSunPkcs11ConfigurationWithoutCredentials() {
        Pkcs11Configuration configuration = new Pkcs11Configuration(
                "Lab Token #1", Path.of("/tmp/libpkcs11-test.so"), 2);

        String rendered = configuration.toSunPkcs11Configuration();
        assertEquals("Lab_Token__1", configuration.name());
        assertTrue(rendered.contains("name = Lab_Token__1"));
        assertTrue(rendered.contains("slotListIndex = 2"));
        assertTrue(rendered.contains("library = "));
        assertTrue(!rendered.toLowerCase().contains("pin"));
    }

    @Test
    void rejectsNegativeSlotIndex() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pkcs11Configuration("Token", Path.of("/tmp/libpkcs11-test.so"), -1));
    }

    @Test
    void sessionFailsClearlyBeforeTryingMissingNativeLibrary() {
        Pkcs11Configuration configuration = new Pkcs11Configuration(
                "Token", Path.of("/definitely-not-present/libpkcs11.so"), 0);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Pkcs11Session.open(configuration, "1234".toCharArray()));
        assertTrue(error.getMessage().contains("library was not found"));
    }

    @Test
    void sharedSessionManagerFailsClosedWithoutAConnectedToken() {
        Pkcs11SessionManager manager = Pkcs11SessionManager.getInstance();
        manager.disconnect();
        IllegalStateException error = assertThrows(IllegalStateException.class, manager::requireSession);
        assertTrue(error.getMessage().contains("No PKCS#11 token is connected"));
    }

    @Test
    void mapsStandardJwsAlgorithmsToTokenJcaNames() {
        assertEquals("SHA256withRSA", Pkcs11JwsSigner.jcaSignatureName(JWSAlgorithm.RS256));
        assertEquals("SHA384withECDSA", Pkcs11JwsSigner.jcaSignatureName(JWSAlgorithm.ES384));
        assertEquals("Ed25519", Pkcs11JwsSigner.jcaSignatureName(JWSAlgorithm.EdDSA));
    }
}
