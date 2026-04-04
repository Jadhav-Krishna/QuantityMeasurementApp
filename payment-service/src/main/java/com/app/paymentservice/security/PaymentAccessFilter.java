package com.app.paymentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class PaymentAccessFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";
    private static final String PAYMENT_PATH_PREFIX = "/api/payments/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresLogin(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeUnauthorized(response, "Please log in to recharge credits or view transactions.");
            return;
        }

        Long userId = extractUserId(auth.substring(7));
        if (userId == null) {
            writeUnauthorized(response, "Your session is invalid. Please log in again.");
            return;
        }

        request.setAttribute(AUTHENTICATED_USER_ID, userId);
        filterChain.doFilter(request, response);
    }

    private boolean requiresLogin(String path) {
        return path != null
                && path.startsWith(PAYMENT_PATH_PREFIX)
                && !"/api/payments/config".equals(path);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }

    private Long extractUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int idx = payload.indexOf("\"userId\"");
            if (idx < 0) {
                return null;
            }

            int colon = payload.indexOf(':', idx);
            int comma = payload.indexOf(',', colon);
            int brace = payload.indexOf('}', colon);
            int end = (comma > 0 && comma < brace) ? comma : brace;
            return Long.parseLong(payload.substring(colon + 1, end).trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
