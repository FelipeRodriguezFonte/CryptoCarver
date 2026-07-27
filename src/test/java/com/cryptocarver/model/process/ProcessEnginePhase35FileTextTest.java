package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessEnginePhase35FileTextTest {
    @TempDir
    Path tempDir;

    @Test
    void phase35_fileInputOutputTextModeHonorsCharset() throws Exception {
        Path inFile = tempDir.resolve("in.txt");
        Path outFile = tempDir.resolve("out.txt");
        String content = "Hola mundo ñ";
        Charset iso88591 = Charset.forName("ISO-8859-1");
        Files.write(inFile, content.getBytes(iso88591));

        ProcessDefinition.Node inNode = new ProcessDefinition.Node("in", "FILE_INPUT", "File In", 0, 0);
        inNode.configuration.put("path", inFile.toString());
        inNode.configuration.put("readMode", "TEXT");
        inNode.configuration.put("charset", "ISO-8859-1");

        ProcessDefinition.Node outNode = new ProcessDefinition.Node("out", "FILE_OUTPUT", "File Out", 0, 0);
        outNode.configuration.put("path", outFile.toString());
        outNode.configuration.put("writeMode", "TEXT");
        outNode.configuration.put("charset", "UTF-16");

        ProcessDefinition.Connection conn = new ProcessDefinition.Connection("in", "out", "input");

        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(inNode);
        def.nodes.add(outNode);
        def.connections.add(conn);

        StringBuilder trace = new StringBuilder();
        ExecutionContext ctx = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {
            trace.append("\n[").append(event.step()).append("] ").append(event.nodeLabel()).append(" · ").append(event.nodeType()).append("\n");
        });

        ProcessEngine.execute(def, ctx);

        byte[] outBytes = Files.readAllBytes(outFile);
        String outContent = new String(outBytes, Charset.forName("UTF-16"));
        assertEquals(content, outContent);

        String t = trace.toString();
        assertTrue(t.contains("[1] File In · FILE_INPUT"));
        assertTrue(t.contains("[2] File Out · FILE_OUTPUT"));
    }

    @Test
    void phase35_fileOutputBinaryWritesExactBytes() throws Exception {
        Path outFile = tempDir.resolve("out_bin.txt");
        byte[] content = {0x01, 0x02, (byte) 0xFF};

        ProcessDefinition.Node inNode = new ProcessDefinition.Node("in", "FILE_INPUT", "File In", 0, 0);
        inNode.configuration.put("path", tempDir.resolve("dummy.txt").toString());
        Files.write(tempDir.resolve("dummy.txt"), content);
        inNode.configuration.put("readMode", "BINARY");

        ProcessDefinition.Node outNode = new ProcessDefinition.Node("out", "FILE_OUTPUT", "File Out", 0, 0);
        outNode.configuration.put("path", outFile.toString());
        outNode.configuration.put("writeMode", "BINARY");

        ProcessDefinition.Connection conn = new ProcessDefinition.Connection("in", "out", "input");

        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(inNode);
        def.nodes.add(outNode);
        def.connections.add(conn);

        ExecutionContext ctx = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {});

        ProcessEngine.execute(def, ctx);

        byte[] outBytes = Files.readAllBytes(outFile);
        assertArrayEquals(content, outBytes);
    }
}
