package com.app.emailservice.controller;

import com.app.emailservice.dto.CreditExhaustedEmailRequest;
import com.app.emailservice.dto.LoginEmailRequest;
import com.app.emailservice.dto.RechargeSuccessEmailRequest;
import com.app.emailservice.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> sendLoginEmail(@RequestBody LoginEmailRequest request) {
        emailService.sendLoginEmail(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/credit-exhausted")
    public ResponseEntity<Void> sendCreditExhaustedEmail(@RequestBody CreditExhaustedEmailRequest request) {
        emailService.sendCreditExhaustedEmail(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/recharge-success")
    public ResponseEntity<Void> sendRechargeSuccessEmail(@RequestBody RechargeSuccessEmailRequest request) {
        emailService.sendRechargeSuccessEmail(request);
        return ResponseEntity.accepted().build();
    }
}
