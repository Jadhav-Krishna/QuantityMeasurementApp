package com.app.emailservice.dto;

import java.time.Instant;

public record RechargeSuccessEmailRequest(
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
