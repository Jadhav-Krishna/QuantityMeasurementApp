package com.app.userservice.dto;

public record SignupEmailRequest(
        String toEmail,
        String userName
) {}
