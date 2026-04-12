package com.app.measurementservice.dto;

import jakarta.validation.constraints.NotNull;

public record QuantityRequest(
        @NotNull QuantityDTO thisQuantityDTO,
        @NotNull QuantityDTO thatQuantityDTO
) {
    public record QuantityDTO(
            @NotNull Double value,
            @NotNull String unit,
            @NotNull String measurementType
    ) {}
}
