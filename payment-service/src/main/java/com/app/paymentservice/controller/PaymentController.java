package com.app.paymentservice.controller;

import com.app.paymentservice.dto.CreateOrderRequest;
import com.app.paymentservice.dto.CreateOrderResponse;
import com.app.paymentservice.dto.PaymentConfigResponse;
import com.app.paymentservice.dto.TransactionResponse;
import com.app.paymentservice.dto.VerifyPaymentRequest;
import com.app.paymentservice.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.app.paymentservice.security.PaymentAccessFilter.AUTHENTICATED_USER_ID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> getConfig() {
        return ResponseEntity.ok(paymentService.getPaymentConfig());
    }

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest
    )
            throws RazorpayException {
        Long authenticatedUserId = requireAuthenticatedUserId(httpRequest);
        if (!authenticatedUserId.equals(request.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only create recharge orders for your own account.");
        }
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<TransactionResponse> verify(
            @Valid @RequestBody VerifyPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long authenticatedUserId = requireAuthenticatedUserId(httpRequest);
        return ResponseEntity.ok(paymentService.verifyAndRecharge(request, authenticatedUserId));
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @PathVariable Long userId,
            HttpServletRequest httpRequest
    ) {
        Long authenticatedUserId = requireAuthenticatedUserId(httpRequest);
        if (!authenticatedUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view transactions for your own account.");
        }
        return ResponseEntity.ok(paymentService.getUserTransactions(userId));
    }

    private Long requireAuthenticatedUserId(HttpServletRequest request) {
        Object value = request != null ? request.getAttribute(AUTHENTICATED_USER_ID) : null;
        if (value instanceof Long userId) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
    }
}
