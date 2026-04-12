package com.app.paymentservice.repository;

import com.app.paymentservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByRazorpayOrderId(String orderId);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
