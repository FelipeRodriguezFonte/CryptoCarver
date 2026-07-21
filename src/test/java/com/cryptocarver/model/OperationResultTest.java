package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OperationResultTest {

    @Test
    void resultDefensivelyCopiesByteArraysAndKeepsDetails() {
        byte[] input = {1, 2};
        OperationResult result = OperationResult.forOperation("Test operation")
                .input(input).output(new byte[] {3})
                .details(Map.of("Algorithm", "Test"))
                .status("Completed").build();

        input[0] = 9;
        assertArrayEquals(new byte[] {1, 2}, result.getInput());
        byte[] read = result.getOutput();
        read[0] = 9;
        assertArrayEquals(new byte[] {3}, result.getOutput());
        assertEquals("Test", result.getDetails().get(0).value());
    }

    @Test
    void operationNameIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> OperationResult.forOperation(" "));
    }
}
