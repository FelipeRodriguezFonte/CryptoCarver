package com.cryptocarver.crypto.iso8583;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Iso8583ReportOrderingCharacterizationTest {
    @Test
    void reportListsFieldsInAscendingNumericOrderRegardlessOfInsertionOrder() {
        var profile = Iso8583Operations.Profile.iso1987();
        var fields = new LinkedHashMap<Integer, Iso8583Operations.ParsedField>();
        add(fields, 41, "TERM0001");
        add(fields, 11, "654321");
        add(fields, 3, "000000");
        add(fields, 49, "840");
        add(fields, 4, "000000001234");
        add(fields, 7, "0102030405");

        var message = new Iso8583Operations.Message(
                Iso8583Operations.Mti.parse("0200"), profile, new byte[8],
                List.of(), fields, List.of());
        String report = message.report();
        List<Integer> actual = new ArrayList<>();
        for (String line : report.split("\\R")) {
            if (line.matches("F\\d{3} .*")) actual.add(Integer.parseInt(line.substring(1, 4)));
        }

        assertEquals(List.of(3, 4, 7, 11, 41, 49), actual, report);
    }

    private static void add(LinkedHashMap<Integer, Iso8583Operations.ParsedField> fields,
                            int number, String value) {
        var definition = Iso8583Operations.fieldDefinition(number, Iso8583Operations.Version.ISO_1987);
        fields.put(number, new Iso8583Operations.ParsedField(
                number, definition, value, 0, List.of(), ""));
    }
}
