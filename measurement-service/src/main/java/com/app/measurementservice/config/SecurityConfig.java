package com.app.measurementservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GuestUsageFilter guestUsageFilter;
    private final HistoryAccessFilter historyAccessFilter;

    public SecurityConfig(GuestUsageFilter guestUsageFilter, HistoryAccessFilter historyAccessFilter) {
        this.guestUsageFilter = guestUsageFilter;
        this.historyAccessFilter = historyAccessFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Measurement compute endpoints — open to guests
                .requestMatchers(
                    "/api/v1/quantities/compare",
                    "/api/v1/quantities/convert",
                    "/api/v1/quantities/add",
                    "/api/v1/quantities/subtract",
                    "/api/v1/quantities/divide",
                    "/api/v1/quantities/my/history",
                    "/api/v1/quantities/my/history/**",
                    "/api/convert/**",
                    "/api/measurements/**",
                    "/actuator/**",
                    "/h2-console/**"
                ).permitAll()
                // History requires a logged-in user (JWT present)
                .anyRequest().authenticated()
            )
            .addFilterBefore(historyAccessFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(guestUsageFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
