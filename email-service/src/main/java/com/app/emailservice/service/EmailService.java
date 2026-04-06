package com.app.emailservice.service;

import com.app.emailservice.dto.CreditExhaustedEmailRequest;
import com.app.emailservice.dto.LoginEmailRequest;
import com.app.emailservice.dto.RechargeSuccessEmailRequest;
import com.app.emailservice.dto.SignupEmailRequest;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

        String body = String.format("""
        <html>
        <body style="font-family: Arial, sans-serif; background-color:#f4f6f8; padding:20px;">
            
            <div style="max-width:600px; margin:auto; background:#ffffff; border-radius:10px; overflow:hidden; box-shadow:0 4px 12px rgba(0,0,0,0.1);">
                
                <!-- Header -->
                <div style="background:#4CAF50; color:white; padding:20px; text-align:center;">
                    <h2 style="margin:0;">Recharge Successful </h2>
                </div>
                
                <div style="padding:20px;">
                    <p style="font-size:16px;">Hi <b>%s</b>,</p>
                    <p>Your recharge was successful! Here are your bill details:</p>
                    
                    <table style="width:100%%; border-collapse:collapse; margin-top:15px;">
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>User ID</b></td><td>%s</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Credits Added</b></td><td>%d</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Total Credits</b></td><td>%d</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Amount Paid</b></td><td>Rs %.2f</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Transaction ID</b></td><td>%s</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Order ID</b></td><td>%s</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Invoice ID</b></td><td>%s</td></tr>
                        <tr><td style="padding:8px; border-bottom:1px solid #eee;"><b>Invoice Number</b></td><td>%s</td></tr>
                        <tr><td style="padding:8px;"><b>Date</b></td><td>%s</td></tr>
                    </table>
                    
                    <p style="margin-top:20px;">Thank you for using <b>Quantity Measurement App</b> </p>
                </div>
                
                <div style="background:#f1f1f1; padding:15px; text-align:center; font-size:12px; color:#777;">
                    This is an automated email. Please do not reply.
                </div>
                
            </div>
            
        </body>
        </html>
        """,

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

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(sender);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);

            log.info("Email sent successfully to '{}' with subject '{}'", to, subject);

        } catch (Exception e) {
            log.error("Error while sending email", e);
        }
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
