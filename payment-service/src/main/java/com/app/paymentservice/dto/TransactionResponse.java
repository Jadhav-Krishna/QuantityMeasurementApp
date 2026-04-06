package com.app.paymentservice.dto;

import com.app.paymentservice.entity.Transaction;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Long userId,
        String razorpayOrderId,
        String razorpayPaymentId,
        Long invoiceId,
        String invoiceNumber,
        long amountPaise,
        int creditsAdded,
        Transaction.Status status,
        Instant createdAt
) {}
