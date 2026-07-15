package com.cryptoforge.crypto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EmvTlvTest {
 @Test void parsesKnownEmvTags() { var items=EmvTlv.parse("9F02060000000001005F2A020978"); assertEquals(2,items.size()); assertEquals("Amount, Authorised",items.get(0).name()); assertEquals("Transaction Currency Code",items.get(1).name()); }
 @Test void rejectsTruncatedValue() { assertThrows(IllegalArgumentException.class,()->EmvTlv.parse("9F02060000")); }
 @Test void interpretsKnownAmount() { assertEquals("amount minor units: 000000000100", EmvTlv.interpretation(EmvTlv.parse("9F0206000000000100").get(0))); }
 @Test void parsesCdolFields() { var dol=EmvTlv.parseDol("9F02069F1A029505"); assertEquals(3,dol.size()); assertEquals("9F02",dol.get(0).tag()); assertEquals(6,dol.get(0).length()); }
 @Test void buildsDolAndZeroFillsMissingFields() { assertEquals("00000000010009780000000000", EmvTlv.buildDol("9F02069F1A029505", java.util.Map.of("9F02","000000000100","9F1A","0978"))); }
 @Test void detailsDolZeroFillsAndIgnoresUnrequestedInputExplicitly() {
  var result=EmvTlv.buildDolDetailed("9F02069F1A02", java.util.Map.of("9F02","000000000100","9C","00"));
  assertEquals("0000000001000000",result.data()); assertTrue(result.fields().get(1).supplied()==false);
  assertTrue(result.warnings().stream().anyMatch(w -> w.contains("9F1A was not supplied"))); assertTrue(result.warnings().stream().anyMatch(w -> w.contains("9C was supplied")));
 }
 @Test void interpretsApplicationLabel() { assertEquals("label: VISA", EmvTlv.interpretation(EmvTlv.parse("500456495341").get(0))); }
 @Test void summarizesTransactionFieldsAndFlagsLengthIssues() {
  var analysis=EmvTlv.analyze("9F02060000000001005F2A0209789A032401319F360200019F2701809F26081122334455667788");
  assertEquals(6,analysis.totalItems()); assertTrue(EmvTlv.transactionSummary(analysis).contains("ARQC")); assertTrue(analysis.warnings().isEmpty());
  assertTrue(EmvTlv.analyze("9F02050000000001").warnings().stream().anyMatch(w -> w.contains("commonly expects 6")));
 }
}
