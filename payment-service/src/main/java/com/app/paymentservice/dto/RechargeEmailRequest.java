package com.app.paymentservice.dto;

import java.time.Instant;

public record RechargeEmailRequest(
        String toEmail,
        String userName,
        int creditsAdded,
        int totalCredits,
        long amountPaise,
        String transactionId,
        Instant rechargedAt
) {}
