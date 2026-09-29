package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GuidedStepPolicyTest {
  @Test
  void hashStepProvidesStableTitleAndDescription() {
    GuidedStepPolicy.StepText step = GuidedStepPolicy.text("HASH", 2);

    assertEquals(
        "Step 2 of 5: Choose algorithm & settings (or Start from a Template)", step.title());
    assertEquals(
        "Select digest algorithm (e.g. SHA-256, SHA-512) or Apply a safe template.",
        step.description());
  }

  @Test
  void invalidStepHasNoText() {
    assertNull(GuidedStepPolicy.text("HASH", 0));
  }
}
