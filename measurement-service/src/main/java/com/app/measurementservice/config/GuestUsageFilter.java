package com.app.measurementservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GuestUsageFilter extends OncePerRequestFilter {

    private static final int FREE_LIMIT = 5;

    private static final Set<String> COMPUTE_PATHS = Set.of(
            "/api/v1/quantities/compare",
            "/api/v1/quantities/convert",
            "/api/v1/quantities/add",
            "/api/v1/quantities/subtract",
            "/api/v1/quantities/divide"
    );

    // IP -> usage count (resets on service restart; good enough for guest throttling)
    private final ConcurrentHashMap<String, AtomicInteger> guestUsage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only apply to compute endpoints
        if (!COMPUTE_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // If the request carries a JWT the user is logged in — skip guest throttle
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveIp(request);
        AtomicInteger count = guestUsage.computeIfAbsent(ip, k -> new AtomicInteger(0));

        if (count.get() >= FREE_LIMIT) {
            response.setStatus(402);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Guest-Uses-Remaining", "0");
            objectMapper.writeValue(response.getWriter(),
                    Map.of("reason", "GUEST_LIMIT",
                           "message", "Your 5 free guest credits are exhausted. Please log in and recharge to continue.",
                           "usedCount", count.get(),
                           "freeLimit", FREE_LIMIT));
            return;
        }

        filterChain.doFilter(request, response);

        if (response.getStatus() < 400) {
            int used = count.incrementAndGet();
            response.setHeader("X-Guest-Uses-Remaining", String.valueOf(Math.max(0, FREE_LIMIT - used)));
        } else {
            response.setHeader("X-Guest-Uses-Remaining", String.valueOf(Math.max(0, FREE_LIMIT - count.get())));
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
