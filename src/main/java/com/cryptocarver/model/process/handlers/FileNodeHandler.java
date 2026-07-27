package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FileWritePolicy;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import java.util.List;
import java.util.Map;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class FileNodeHandler implements ProcessNodeHandler {
    @Override
    public Set<String> supportedTypes() {
        return Set.of("FILE_INPUT", "FILE_OUTPUT");
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        if ("FILE_INPUT".equals(node.type)) return List.of();
        return List.of(new PortDefinition("input", Set.of(Representation.values()), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if ("FILE_INPUT".equals(node.type)) {
            String mode = node.configuration.getOrDefault("readMode", "BINARY");
            return "TEXT".equals(mode) ? Representation.TEXT_UTF8 : Representation.BINARY;
        }
        return inputs.getOrDefault("input", Representation.BINARY);
    }

    private java.io.File resolveFile(ProcessDefinition.Node node) {
        String path = node.configuration.get("filePath");
        if (path == null) path = node.configuration.get("path"); // fallback for tests
        if (path == null) throw new IllegalArgumentException("No filePath configured for " + node.id);
        return new java.io.File(path);
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        java.io.File file = resolveFile(node);
        if ("FILE_INPUT".equals(node.type)) {
            String mode = node.configuration.getOrDefault("readMode", "BINARY");
            if ("TEXT".equals(mode)) {
                String charsetName = node.configuration.getOrDefault("charset", "UTF-8");
                Charset charset = Charset.forName(charsetName);
                String content = java.nio.file.Files.readString(file.toPath(), charset);
                return FlowValue.text(content, charset);
            } else {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                return FlowValue.binary(bytes);
            }
        } else {
            FlowValue input = inputs.getOrDefault("input", FlowValue.binary(new byte[0]));
            if (context.fileWritePolicy() == com.cryptocarver.model.process.FileWritePolicy.FAIL_IF_EXISTS && file.exists()) {
                throw new IllegalStateException("File already exists: " + file.getAbsolutePath());
            }

            String mode = node.configuration.getOrDefault("writeMode", node.configuration.getOrDefault("readMode", "BINARY"));
            if ("TEXT".equals(mode)) {
                String charsetName = node.configuration.getOrDefault("charset", "UTF-8");
                Charset charset = Charset.forName(charsetName);
                String content = input.render();
                java.nio.file.Files.write(file.toPath(), content.getBytes(charset));
            } else {
                java.nio.file.Files.write(file.toPath(), input.bytes());
            }
            return input;
        }
    }
}
