package com.app.emailservice.service;

import com.app.emailservice.dto.CreditExhaustedEmailRequest;
import com.app.emailservice.dto.LoginEmailRequest;
import com.app.emailservice.dto.RechargeSuccessEmailRequest;
import com.app.emailservice.dto.SignupEmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@quantitymeasurement.app}")
    private String from;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendLoginEmail(LoginEmailRequest req) {
        send(req.toEmail(), "Welcome back to Quantity Measurement!",
                "Hi " + req.userName() + ",\n\nYou have successfully logged in.\n\nHappy measuring!");
    }

    public void sendSignupEmail(SignupEmailRequest req) {
        send(req.toEmail(), "Welcome to Quantity Measurement!",
                "Hi " + req.userName() + ",\n\nYour account has been created successfully.\n"
                        + "You can now use your free credits and recharge whenever needed.\n\nHappy measuring!");
    }

    public void sendCreditExhaustedEmail(CreditExhaustedEmailRequest req) {
        send(req.toEmail(), "Your credits are exhausted",
                "Hi " + req.userName() + ",\n\nYou have used all your available credits.\n"
                        + "Please recharge to continue using the measurement service.");
    }

    public void sendRechargeSuccessEmail(RechargeSuccessEmailRequest req) {
        String body = String.format(
                "Hi %s,%n%nYour recharge was successful!%n%n"
                        + "--- Bill ---%n"
                        + "User ID        : %s%n"
                        + "Credits Added  : %d%n"
                        + "Total Credits  : %d%n"
                        + "Amount Paid    : Rs %.2f%n"
                        + "Transaction ID : %s%n"
                        + "Order ID       : %s%n"
                        + "Invoice ID     : %s%n"
                        + "Invoice Number : %s%n"
                        + "Date           : %s%n%n"
                        + "Thank you for using Quantity Measurement App!",
                req.userName(),
                req.userId(),
                req.creditsAdded(),
                req.totalCredits(),
                req.amountPaise() / 100.0,
                req.transactionId(),
                req.razorpayOrderId(),
                req.invoiceId(),
                req.invoiceNumber(),
                req.rechargedAt()
        );
        send(req.toEmail(), "Recharge Successful - Your Bill", body);
    }

    private void send(String to, String subject, String body) {
        String sender = resolveSender();
        if (to == null || to.isBlank() || sender == null || sender.isBlank()) {
            log.warn("Email skipped. Missing to/sender address. to='{}', sender='{}'", to, sender);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(sender);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.info("Email sent successfully to '{}' with subject '{}'", to, subject);
    }

    private String resolveSender() {
        if (isValidEmail(from)) {
            return from;
        }
        if (isValidEmail(mailUsername)) {
            return mailUsername;
        }
        return null;
    }

    private boolean isValidEmail(String value) {
        return value != null && !value.isBlank() && value.contains("@");
    }
}
