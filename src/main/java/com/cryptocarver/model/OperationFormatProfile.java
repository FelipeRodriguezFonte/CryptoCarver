package com.cryptocarver.model;

import java.util.List;

/**
 * Defines the format contract for an operation.
 */
public record OperationFormatProfile(
        String operationPath,
        List<String> allowedInputFormats,
        List<String> allowedOutputFormats,
        String defaultInputFormat,
        String defaultOutputFormat,
        DataType inputDataType,
        DataType outputDataType,
        String contractDescription
) {
    public OperationFormatProfile {
        if (operationPath == null || operationPath.isBlank()) {
            throw new IllegalArgumentException("Operation path cannot be blank");
        }
        allowedInputFormats = allowedInputFormats != null ? List.copyOf(allowedInputFormats) : List.of();
        allowedOutputFormats = allowedOutputFormats != null ? List.copyOf(allowedOutputFormats) : List.of();
        if (inputDataType == null) inputDataType = DataType.BYTES;
        if (outputDataType == null) outputDataType = DataType.BYTES;
        if (contractDescription == null) contractDescription = "";
    }
}
