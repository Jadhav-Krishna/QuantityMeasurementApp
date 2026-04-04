package com.app.paymentservice.dto;

import java.util.List;

public record PaymentConfigResponse(
        String razorpayKeyId,
        int creditsPerUnit,
        long pricePerUnitPaise,
        List<Integer> availableUnits
) {
}
