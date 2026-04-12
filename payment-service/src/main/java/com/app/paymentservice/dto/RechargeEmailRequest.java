package com.app.paymentservice.dto;

import java.time.Instant;

public record RechargeEmailRequest(
        String toEmail,
        String userName,
        Long userId,
        int creditsAdded,
        int totalCredits,
        long amountPaise,
        String transactionId,
        String razorpayOrderId,
        Long invoiceId,
        String invoiceNumber,
        Instant rechargedAt
) {}
