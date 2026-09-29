package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellTextResolverTest {
  private final ShellTextResolver resolver =
      new ShellTextResolver(
          key ->
              Map.of(
                      "bread.symmetricKeys", "Claves simétricas",
                      "bread.symmetric", "Simétrico",
                      "error.wrap.tag.title", "Error traducido",
                      "error.wrap.tag.detail", "Detalle traducido",
                      "error.wrap.tag.remedy", "Solución traducida")
                  .getOrDefault(key, key));

  @Test
  void translatesKnownSectionText() {
    assertEquals("Claves simétricas", resolver.localizedSectionText("Symmetric Keys"));
  }

  @Test
  void preservesUnknownSectionText() {
    assertEquals("Custom section", resolver.localizedSectionText("Custom section"));
  }

  @Test
  void translatesKnownModuleText() {
    assertEquals("Simétrico", resolver.localizedModuleText("Symmetric"));
  }

  @Test
  void preservesUnknownModuleText() {
    assertEquals("Custom module", resolver.localizedModuleText("Custom module"));
  }

  @Test
  void localizesMappedError() {
    UserFacingError error =
        new UserFacingError("Authentication Tag Verification Failed", "detail", "remedy", "tag");
    UserFacingError localized = resolver.localizedError(error);
    assertEquals("Error traducido", localized.title());
    assertEquals("Detalle traducido", localized.detail());
    assertEquals("Solución traducida", localized.remedy());
    assertEquals("tag", localized.fieldKey());
  }

  @Test
  void preservesUnmappedError() {
    UserFacingError error = new UserFacingError("Custom failure", "detail", "remedy", "field");
    assertSame(error, resolver.localizedError(error));
  }
}
