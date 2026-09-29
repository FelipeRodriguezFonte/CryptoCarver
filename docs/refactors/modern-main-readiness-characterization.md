# Readiness and guided flow characterization

The characterization loads the production `/fxml/main-view-modern.fxml` using `Fxml.loader`. Maven supplies `user.home=target/test-home`. The Spanish/English case stores the prior `AppSettings` language preference and restores it in `@AfterEach`.

`ModernMainReadinessCharacterizationTest` covers each behavior in a separate test:

- `symmetricEncryptionPreflightHasExpectedLiteralMessages`: incomplete symmetric encryption reports exactly `Input payload is empty. Enter or paste data to process.`
- `symmetricDecryptionPreflightRequiresAuthenticationTag`: GCM decryption without a tag reports exactly `AEAD decryption requires an Authentication Tag.`
- `operationWithoutPreflightRequirementsReturnsNoReport`: Manual Conversion returns no preflight report.
- `readinessPanelHidesAfterHashInputBecomesComplete`: an empty hash input shows the panel; adding input hides it.
- `guidedStepMovesForwardAndBackWithNavigationButtons`: the production Next and Back buttons render steps 2 and 1 respectively.
- `readinessSummaryUsesSpanishAndEnglishCatalogEntries`: incomplete readiness uses the Spanish summary, then the English summary.

Characterization command and output:

```text
$ nice -n 19 mvn -o -q -Dtest=ModernMainReadinessCharacterizationTest test
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```
