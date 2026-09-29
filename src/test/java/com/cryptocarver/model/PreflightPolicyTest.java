package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PreflightPolicyTest {
  @Test
  void hashingPolicyProducesIncompleteInputReport() {
    PreflightReport report =
        PreflightPolicy.evaluate(
            "Hashing",
            true,
            new PreflightPolicy.Inputs(
                "",
                "Text (UTF-8)",
                "SHA-256",
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null));

    assertEquals(PreflightStatus.INCOMPLETE, report.getOverallStatus());
    assertEquals(
        "Input payload is empty. Enter text to hash.", report.getChecks().get(0).getMessage());
  }

  @Test
  void unsupportedOperationDoesNotRequirePreflight() {
    assertNull(
        PreflightPolicy.evaluate(
            "Manual Conversion",
            true,
            new PreflightPolicy.Inputs(
                null, null, null, null, null, null, null, null, false, null, null, null, null)));
  }
}
