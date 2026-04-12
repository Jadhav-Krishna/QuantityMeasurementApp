package com.app.userservice.controller;

import com.app.userservice.service.UserApplicationService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class CreditController {

    private final UserApplicationService userApplicationService;
    private final ControllerAccessSupport controllerAccessSupport;

    public CreditController(
            UserApplicationService userApplicationService,
            ControllerAccessSupport controllerAccessSupport
    ) {
        this.userApplicationService = userApplicationService;
        this.controllerAccessSupport = controllerAccessSupport;
    }

    @GetMapping("/{userId}/credits")
    public ResponseEntity<Map<String, Integer>> getCredits(@PathVariable Long userId, Authentication authentication) {
        controllerAccessSupport.requireUserAccess(userId, authentication);
        return ResponseEntity.ok(Map.of("credits", userApplicationService.getCredits(userId)));
    }

    @PostMapping("/{userId}/credits/add")
    public ResponseEntity<Map<String, Integer>> addCredits(
            @PathVariable Long userId,
            @RequestParam int amount) {
        return ResponseEntity.ok(Map.of("credits", userApplicationService.addCredits(userId, amount)));
    }

    @PostMapping("/{userId}/credits/deduct")
    public ResponseEntity<Map<String, Integer>> deductCredit(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("credits", userApplicationService.deductCredit(userId)));
    }
}
