package com.cryptocarver.asn1;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

public class ASN1EditorTest {

    @Test
    public void testEncodeLength() {
        assertArrayEquals(new byte[]{0x05}, ASN1Editor.encodeLength(5));
        assertArrayEquals(new byte[]{0x7F}, ASN1Editor.encodeLength(127));
        assertArrayEquals(new byte[]{(byte)0x81, (byte)0x80}, ASN1Editor.encodeLength(128));
        assertArrayEquals(new byte[]{(byte)0x82, 0x01, 0x00}, ASN1Editor.encodeLength(256));
    }

    @Test
    public void testEditNodeAndReencode() throws IOException {
        // Original: SEQUENCE { INTEGER 5, INTEGER 10 }
        // 30 06 02 01 05 02 01 0A
        byte[] original = new byte[]{0x30, 0x06, 0x02, 0x01, 0x05, 0x02, 0x01, 0x0A};
        ASN1TreeNode root = ASN1Parser.parse(original);

        // Edit second integer from 10 to 300 (which requires 2 bytes)
        // New integer: 02 02 01 2C
        byte[] newInteger = new byte[]{0x02, 0x02, 0x01, 0x2C};

        ASN1TreeNode target = root.getChildren().get(1); // The second integer

        byte[] result = ASN1Editor.editNodeAndReencode(root, target, newInteger);

        // Expected: SEQUENCE { INTEGER 5, INTEGER 300 }
        // 30 07 02 01 05 02 02 01 2C
        byte[] expected = new byte[]{0x30, 0x07, 0x02, 0x01, 0x05, 0x02, 0x02, 0x01, 0x2C};
        assertArrayEquals(expected, result);
    }
}
