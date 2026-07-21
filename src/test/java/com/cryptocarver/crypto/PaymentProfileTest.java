package com.cryptocarver.crypto;

import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.model.payments.PaymentProfileManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class PaymentProfileTest {

    @Test
    public void testAllPaymentProfiles() {
        List<PaymentProfile> profiles = PaymentProfileManager.getAllProfiles();
        assertFalse(profiles.isEmpty(), "Profiles should not be empty");

        for (PaymentProfile profile : profiles) {
            VerificationResult result = PaymentProfileVerifier.verify(profile);
            assertTrue(result.isSuccess(), "Profile " + profile.getId() + " failed: " + result.getMessage());
        }
    }

    @Test
    public void testStrictBuilderValidation() {
        // Test missing TR31 arguments
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.TR31).build();
        });

        // Test missing PIN format
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.PIN)
                .addInput("pin", "1234")
                .build();
        });

        // Test missing EMV
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.EMV).build();
        });

        // Test DUKPT TDES missing bdk/ksn
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.DUKPT_TDES).build();
        });

        // Test DUKPT AES missing scheme
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.DUKPT_AES)
                .addInput("bdk", "123")
                .addInput("ksn", "123")
                .build();
        });
    }

    @Test
    public void testImmutability() {
        PaymentProfile profile = new PaymentProfile.Builder("id", "name", "1.0", PaymentProfile.ProfileType.PIN)
            .addParameter("format", "ISO 0")
            .addInput("pin", "1234")
            .addOutput("pinBlock", "1234")
            .build();

        assertThrows(UnsupportedOperationException.class, () -> profile.getParameters().put("new", "val"));
        assertThrows(UnsupportedOperationException.class, () -> profile.getInputs().put("new", "val"));
        assertThrows(UnsupportedOperationException.class, () -> profile.getOutputs().put("new", "val"));
    }
}
