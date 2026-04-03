package com.app.measurementservice.dto;

public record HistoryItemResponse(
        double thisValue,
        String thisUnit,
        String thisMeasurementType,
        double thatValue,
        String thatUnit,
        String thatMeasurementType,
        String operation,
        String resultString,
        Double resultValue,
        String resultUnit,
        String resultMeasurementType,
        String errorMessage,
        boolean error
) {}
