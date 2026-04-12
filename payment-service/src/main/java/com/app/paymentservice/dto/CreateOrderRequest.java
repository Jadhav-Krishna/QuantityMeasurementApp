package com.app.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotNull String userEmail,
        @NotNull String userName,
        @Min(1) int units   // each unit = 5 credits
) {}
