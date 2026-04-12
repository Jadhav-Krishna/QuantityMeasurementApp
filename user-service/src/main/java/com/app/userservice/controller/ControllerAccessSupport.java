package com.app.userservice.controller;

import com.app.userservice.security.CustomUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ControllerAccessSupport {

    public void requireUserAccess(Long userId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
        }

        Long authenticatedUserId = principal.getUser().getId();
        String role = principal.getUser().getRole();
        if (!userId.equals(authenticatedUserId) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own account data.");
        }
    }
}
