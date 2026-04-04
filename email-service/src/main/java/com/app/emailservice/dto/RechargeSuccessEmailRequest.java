package com.app.emailservice.dto;

import java.time.Instant;

public record RechargeSuccessEmailRequest(
        String toEmail,
        String userName,
        int creditsAdded,
        int totalCredits,
        long amountPaise,
        String transactionId,
        Instant rechargedAt
) {}
