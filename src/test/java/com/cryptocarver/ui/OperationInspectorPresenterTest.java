package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationInspectorPresenterTest {

    @Test
    void providesRegistryBackedContextBeforeAnOperationRuns() {
        List<OperationDetail> details = OperationInspectorPresenter.contextDetails("Symmetric Ciphers");

        assertTrue(details.stream().anyMatch(detail -> detail.name().equals("Purpose")
                && detail.value().contains("Encrypt/Decrypt")));
        assertTrue(details.stream().anyMatch(detail -> detail.name().equals("Maturity")
                && detail.value().equals("STABLE")));
        assertTrue(details.stream().anyMatch(detail -> detail.name().equals("Sensitivity")
                && detail.value().equals("HIGH")));
        assertTrue(details.stream().anyMatch(detail -> detail.name().equals("Expected input")
                && detail.value().contains("IV/nonce")));
        assertTrue(details.stream().anyMatch(detail -> detail.name().equals("Produces")
                && detail.value().contains("authentication tag")));
    }

    @Test
    void unknownOperationsDoNotInventContext() {
        assertTrue(OperationInspectorPresenter.contextDetails("Unknown operation").isEmpty());
    }
}
