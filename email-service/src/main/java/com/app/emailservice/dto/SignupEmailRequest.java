package com.app.emailservice.dto;

public record SignupEmailRequest(
        String toEmail,
        String userName
) {}
