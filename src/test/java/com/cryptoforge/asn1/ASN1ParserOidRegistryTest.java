package com.cryptoforge.asn1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ASN1ParserOidRegistryTest {
    @TempDir Path temp;
    @Test void loadsLocalOidNames() throws Exception {
        Path registry = temp.resolve("oids.json");
        Files.writeString(registry, "{\"1.2.3.4\":\"Laboratory OID\"}");
        assertEquals(1, ASN1Parser.loadCustomOidNames(registry));
        ASN1TreeNode tree = ASN1Parser.parse(new byte[] { 0x06, 0x03, 0x2A, 0x03, 0x04 });
        assertTrue(tree.getDecodedValue().contains("Laboratory OID"));
    }
}
