package com.app.paymentservice.service;

import com.app.paymentservice.client.EmailServiceClient;
import com.app.paymentservice.client.UserServiceClient;
import com.app.paymentservice.dto.CreateOrderRequest;
import com.app.paymentservice.dto.CreateOrderResponse;
import com.app.paymentservice.dto.PaymentConfigResponse;
import com.app.paymentservice.dto.RechargeEmailRequest;
import com.app.paymentservice.dto.TransactionResponse;
import com.app.paymentservice.dto.VerifyPaymentRequest;
import com.app.paymentservice.entity.Transaction;
import com.app.paymentservice.repository.TransactionRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RazorpayClient razorpayClient;
    private final TransactionRepository transactionRepository;
    private final UserServiceClient userServiceClient;
    private final EmailServiceClient emailServiceClient;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${app.credits.per-recharge-unit:5}")
    private int creditsPerUnit;

    @Value("${app.credits.price-per-unit-paise:100}")
    private long pricePerUnitPaise;

    private static final List<Integer> AVAILABLE_UNITS = List.of(1, 2, 5, 10);

    public PaymentService(RazorpayClient razorpayClient,
                          TransactionRepository transactionRepository,
                          UserServiceClient userServiceClient,
                          EmailServiceClient emailServiceClient) {
        this.razorpayClient = razorpayClient;
        this.transactionRepository = transactionRepository;
        this.userServiceClient = userServiceClient;
        this.emailServiceClient = emailServiceClient;
    }

    public CreateOrderResponse createOrder(CreateOrderRequest req) throws RazorpayException {
        validateUnits(req.units());
        long amountPaise = req.units() * pricePerUnitPaise;
        int creditsToAdd = req.units() * creditsPerUnit;

        JSONObject options = new JSONObject();
        options.put("amount", amountPaise);
        options.put("currency", "INR");
        options.put("receipt", "rcpt_" + req.userId() + "_" + System.currentTimeMillis());

        Order order = razorpayClient.orders.create(options);
        String razorpayOrderId = order.get("id");

        Transaction tx = new Transaction();
        tx.setUserId(req.userId());
        tx.setUserEmail(req.userEmail());
        tx.setUserName(req.userName());
        tx.setRazorpayOrderId(razorpayOrderId);
        tx.setAmountPaise(amountPaise);
        tx.setCreditsToAdd(creditsToAdd);
        tx.setStatus(Transaction.Status.CREATED);
        transactionRepository.save(tx);

        return new CreateOrderResponse(razorpayOrderId, amountPaise, "INR", creditsToAdd);
    }

    public PaymentConfigResponse getPaymentConfig() {
        return new PaymentConfigResponse(keyId, creditsPerUnit, pricePerUnitPaise, AVAILABLE_UNITS);
    }

    public TransactionResponse verifyAndRecharge(VerifyPaymentRequest req, Long authenticatedUserId) {
        Transaction tx = transactionRepository.findByRazorpayOrderId(req.razorpayOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + req.razorpayOrderId()));

        if (!tx.getUserId().equals(authenticatedUserId)) {
            throw new IllegalArgumentException("This payment does not belong to the logged-in user.");
        }

        // Idempotent — already processed
        if (tx.getStatus() == Transaction.Status.SUCCESS) {
            return toResponse(tx);
        }

        if (!verifySignature(req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            tx.setStatus(Transaction.Status.FAILED);
            transactionRepository.save(tx);
            throw new IllegalArgumentException("Payment signature verification failed");
        }

        tx.setRazorpayPaymentId(req.razorpayPaymentId());
        tx.setRazorpaySignature(req.razorpaySignature());
        tx.setStatus(Transaction.Status.SUCCESS);
        Transaction saved = transactionRepository.save(tx);

        // Add credits to user account
        int updatedCredits = 0;
        try {
            updatedCredits = userServiceClient.addCredits(saved.getUserId(), saved.getCreditsToAdd());
        } catch (Exception e) {
            log.error("Failed to add credits for user {}: {}", saved.getUserId(), e.getMessage());
        }

        // Send bill email (best-effort)
        try {
            emailServiceClient.sendRechargeSuccessEmail(new RechargeEmailRequest(
                    saved.getUserEmail(),
                    saved.getUserName(),
                    saved.getCreditsToAdd(),
                    updatedCredits,
                    saved.getAmountPaise(),
                    saved.getRazorpayPaymentId(),
                    Instant.now()
            ));
        } catch (Exception e) {
            log.warn("Failed to send recharge email for user {}: {}", saved.getUserId(), e.getMessage());
        }

        return toResponse(saved);
    }

    public List<TransactionResponse> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    private void validateUnits(int units) {
        if (!AVAILABLE_UNITS.contains(units)) {
            throw new IllegalArgumentException("Unsupported recharge plan selected.");
        }
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getUserId(), tx.getRazorpayOrderId(),
                tx.getRazorpayPaymentId(), tx.getAmountPaise(),
                tx.getCreditsToAdd(), tx.getStatus(), tx.getCreatedAt()
        );
    }
}
