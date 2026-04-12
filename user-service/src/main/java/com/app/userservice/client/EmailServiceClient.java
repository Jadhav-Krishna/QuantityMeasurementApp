package com.app.userservice.client;

import com.app.userservice.dto.CreditExhaustedEmailRequest;
import com.app.userservice.dto.LoginEmailRequest;
import com.app.userservice.dto.SignupEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "email-service")
public interface EmailServiceClient {

    @PostMapping("/api/emails/login")
    void sendLoginEmail(@RequestBody LoginEmailRequest request);

    @PostMapping("/api/emails/signup")
    void sendSignupEmail(@RequestBody SignupEmailRequest request);

    @PostMapping("/api/emails/credit-exhausted")
    void sendCreditExhaustedEmail(@RequestBody CreditExhaustedEmailRequest request);
}
