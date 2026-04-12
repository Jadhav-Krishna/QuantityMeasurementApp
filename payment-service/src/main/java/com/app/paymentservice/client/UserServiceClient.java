package com.app.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/users/{userId}/credits/add")
    Map<String, Integer> addCreditsRaw(
            @PathVariable("userId") Long userId,
            @RequestParam("amount") int amount
    );

    default int addCredits(Long userId, int amount) {
        Map<String, Integer> result = addCreditsRaw(userId, amount);
        return result != null ? result.getOrDefault("credits", 0) : 0;
    }
}
