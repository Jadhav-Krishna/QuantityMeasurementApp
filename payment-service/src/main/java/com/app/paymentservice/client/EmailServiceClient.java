package com.app.paymentservice.client;

import com.app.paymentservice.dto.RechargeEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "email-service")
public interface EmailServiceClient {

    @PostMapping("/api/emails/recharge-success")
    void sendRechargeSuccessEmail(@RequestBody RechargeEmailRequest request);
}
