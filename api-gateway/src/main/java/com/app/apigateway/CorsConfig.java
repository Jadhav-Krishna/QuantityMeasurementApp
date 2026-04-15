package com.app.apigateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost,http://127.0.0.1,http://localhost:*,http://127.0.0.1:*,https://localhost,https://127.0.0.1,https://localhost:*,https://127.0.0.1:*}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(parseAllowedOrigins());
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setMaxAge(3600L);
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Guest-Uses-Remaining"));
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    private List<String> parseAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .forEach(origins::add);
        return List.copyOf(origins);
    }
}
