package com.app.userservice.security;

import com.app.userservice.config.FrontendRedirectResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final FrontendRedirectResolver frontendRedirectResolver;
    private final JwtService jwtService;

    public OAuth2AuthenticationSuccessHandler(FrontendRedirectResolver frontendRedirectResolver, JwtService jwtService) {
        this.frontendRedirectResolver = frontendRedirectResolver;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof CustomUserPrincipal customUser) {
            logger.info("OAuth2 authentication successful for user: {}", customUser.getEmail());
            String token = jwtService.generateToken(customUser.getUser());
            response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

            String redirectUri = UriComponentsBuilder
                    .fromUriString(frontendRedirectResolver.resolveAuthPageUri(request))
                    .queryParam("token", token)
                    .queryParam("provider", customUser.getUser().getAuthProvider())
                    .build()
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, redirectUri);
            return;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
