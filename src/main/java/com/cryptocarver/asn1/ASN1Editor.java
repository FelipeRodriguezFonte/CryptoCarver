package com.cryptocarver.asn1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class ASN1Editor {

    public static byte[] editNodeAndReencode(ASN1TreeNode root, ASN1TreeNode targetNode, byte[] newEncodedTarget) throws IOException {
        return reencodeNode(root, targetNode, newEncodedTarget);
    }

    private static byte[] reencodeNode(ASN1TreeNode node, ASN1TreeNode targetNode, byte[] newEncodedTarget) throws IOException {
        if (node == targetNode) {
            return newEncodedTarget;
        }

        if (!node.isConstructed() || node.getChildren().isEmpty()) {
            return node.getRawValue();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (ASN1TreeNode child : node.getChildren()) {
            byte[] childBytes = reencodeNode(child, targetNode, newEncodedTarget);
            baos.write(childBytes);
        }
        byte[] newContent = baos.toByteArray();

        byte[] oldRaw = node.getRawValue();
        int tagLen = 1;
        if ((oldRaw[0] & 0x1F) == 0x1F) {
            while ((oldRaw[tagLen++] & 0x80) != 0) {
                // pass
            }
        }
        byte[] tagBytes = Arrays.copyOf(oldRaw, tagLen);
        byte[] lengthBytes = encodeLength(newContent.length);

        ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
        finalOut.write(tagBytes);
        finalOut.write(lengthBytes);
        finalOut.write(newContent);

        return finalOut.toByteArray();
    }

    public static byte[] encodeLength(int length) {
        if (length <= 127) {
            return new byte[] { (byte) length };
        }
        int numBytes = 1;
        int temp = length >>> 8;
        while (temp > 0) {
            numBytes++;
            temp >>>= 8;
        }
        byte[] result = new byte[1 + numBytes];
        result[0] = (byte) (0x80 | numBytes);
        int shift = (numBytes - 1) * 8;
        for (int i = 1; i <= numBytes; i++) {
            result[i] = (byte) ((length >>> shift) & 0xFF);
            shift -= 8;
        }
        return result;
    }
}
