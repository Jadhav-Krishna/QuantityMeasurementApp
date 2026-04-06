package com.app.emailservice.service;

import com.app.emailservice.dto.CreditExhaustedEmailRequest;
import com.app.emailservice.dto.LoginEmailRequest;
import com.app.emailservice.dto.RechargeSuccessEmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@quantitymeasurement.app}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendLoginEmail(LoginEmailRequest req) {
        send(req.toEmail(), "Welcome back to Quantity Measurement!",
                "Hi " + req.userName() + ",\n\nYou have successfully logged in.\n\nHappy measuring!");
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
        if (to == null || to.isBlank() || from == null || from.isBlank()) {
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
