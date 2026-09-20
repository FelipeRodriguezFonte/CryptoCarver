package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.Iso8583NodeHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Iso8583NodeHandlerTest {
    private final Iso8583NodeHandler handler = new Iso8583NodeHandler();

    @Test
    void descriptorsMarkMessageAndFieldsAsTransientSecrets() {
        assertTrue(NodeCatalog.isSensitive(Iso8583NodeHandler.PARSE, "message"));
        assertTrue(NodeCatalog.isSensitive(Iso8583NodeHandler.BUILD, "fields"));
        assertTrue(NodeCatalog.isSensitive(Iso8583NodeHandler.INSPECT, "message"));
    }

    @Test
    void everyIsoNodeRejectsAnEmptyConfiguration() {
        for (String type : Iso8583NodeHandler.TYPES) {
            ProcessDefinition.Node node = new ProcessDefinition.Node("n", type, type, 0, 0);
            assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(node), type);
        }
    }

    @Test
    void buildAndParseReferenceAsciiHexMessage() {
        ProcessDefinition.Node build = new ProcessDefinition.Node("b", Iso8583NodeHandler.BUILD, "build", 0, 0);
        build.configuration.putAll(Map.of("mti", "0200", "fields", "3=000000\n4=000000001000\n11=123456"));
        FlowValue wire = handler.execute(build, Map.of(), null);
        assertTrue(wire.render().startsWith("0200"));
        assertTrue(wire.render().contains("000000001000"));

        ProcessDefinition.Node parse = new ProcessDefinition.Node("p", Iso8583NodeHandler.PARSE, "parse", 0, 0);
        parse.configuration.put("messageFromFlow", "true");
        FlowValue report = handler.execute(parse, Map.of("message", wire), null);
        assertTrue(report.render().contains("F003"));
        assertTrue(report.render().contains("F004"));
        assertTrue(report.render().contains("F011"));
    }
}
