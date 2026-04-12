package com.app.userservice.controller;

import com.app.userservice.dto.AuthResponse;
import com.app.userservice.dto.LoginRequest;
import com.app.userservice.dto.RegisterRequest;
import com.app.userservice.dto.UserResponse;
import com.app.userservice.entity.UserProfile;
import com.app.userservice.security.CustomUserPrincipal;
import com.app.userservice.security.JwtService;
import com.app.userservice.service.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserApplicationService userApplicationService;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserApplicationService userApplicationService,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.userApplicationService = userApplicationService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfile user = userApplicationService.registerLocalUser(request.name(), request.email(), request.password());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(buildAuthResponse(user, token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        UserProfile user = userApplicationService.updateLastLogin(principal.getUser());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(buildAuthResponse(user, token));
    }

    @GetMapping("/user")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        UserProfile user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userApplicationService.toUserResponse(user));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        UserProfile user = resolveUser(authentication);
        boolean authenticated = user != null;
        return ResponseEntity.ok(Map.of(
                "authenticated", authenticated,
                "user", authenticated ? userApplicationService.toUserResponse(user) : Map.of()
        ));
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> loginInfo() {
        return ResponseEntity.ok(Map.of(
                "message", "Use POST /auth/login for JWT login or /oauth2/authorization/google for Google OAuth2 login",
                "loginUrl", "/oauth2/authorization/google",
                "jwtLoginUrl", "/auth/login",
                "registerUrl", "/auth/register",
                "oauth2LoginUrl", "/oauth2/authorization/google"
        ));
    }

    private AuthResponse buildAuthResponse(UserProfile user, String token) {
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs() / 1000, userApplicationService.toUserResponse(user));
    }

    private UserProfile resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            return principal.getUser();
        }

        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null) {
                return userApplicationService.findByEmail(email).orElse(null);
            }
        }

        return null;
    }
}
