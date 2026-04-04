package com.app.paymentservice.dto;

public record CreateOrderResponse(
        String razorpayOrderId,
        long amountPaise,
        String currency,
        int creditsToAdd
) {}
