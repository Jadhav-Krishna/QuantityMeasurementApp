package com.app.userservice.controller;

import com.app.userservice.dto.CreateUserRequest;
import com.app.userservice.dto.UserResponse;
import com.app.userservice.service.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;
    private final ControllerAccessSupport controllerAccessSupport;

    public UserController(
            UserApplicationService userApplicationService,
            ControllerAccessSupport controllerAccessSupport
    ) {
        this.userApplicationService = userApplicationService;
        this.controllerAccessSupport = controllerAccessSupport;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userApplicationService.createUser(request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId, Authentication authentication) {
        controllerAccessSupport.requireUserAccess(userId, authentication);
        return ResponseEntity.ok(userApplicationService.getUser(userId));
    }
}
