package com.cryptoforge.asn1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ASN1TreeExporterTest {
    @Test void exportsTreeAsJsonAndMarkdown() throws Exception {
        ASN1TreeNode root = ASN1Parser.parse(new byte[] { 0x30, 0x03, 0x02, 0x01, 0x01 });
        assertTrue(ASN1TreeExporter.toJson(root).contains("children"));
        assertTrue(ASN1TreeExporter.toMarkdown(root).contains("ASN.1 Structure"));
    }
}
