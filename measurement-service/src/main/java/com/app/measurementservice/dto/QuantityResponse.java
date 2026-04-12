package com.app.measurementservice.dto;

public record QuantityResponse(
        String operation,
        String resultString,
        Double resultValue,
        String resultUnit,
        boolean error,
        String errorMessage,
        String message
) {
    public static QuantityResponse ofNumeric(String operation, Double resultValue, String resultUnit, String message) {
        return new QuantityResponse(operation, null, resultValue, resultUnit, false, null, message);
    }

    public static QuantityResponse ofComparison(boolean equal) {
        return new QuantityResponse("compare", String.valueOf(equal), null, null, false, null, "Comparison completed");
    }

    public static QuantityResponse ofError(String operation, String errorMessage) {
        return new QuantityResponse(operation, null, null, null, true, errorMessage, errorMessage);
    }
}
