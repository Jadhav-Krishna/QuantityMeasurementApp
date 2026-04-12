package com.app.userservice.security;

import com.app.userservice.entity.UserProfile;
import com.app.userservice.service.UserApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService extends OidcUserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOidcUserService.class);

    private final UserApplicationService userApplicationService;

    public CustomOidcUserService(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String googleId = oidcUser.getAttribute("sub");
        String email = oidcUser.getAttribute("email");
        String name = oidcUser.getAttribute("name");
        String givenName = oidcUser.getAttribute("given_name");
        String familyName = oidcUser.getAttribute("family_name");
        String pictureUrl = oidcUser.getAttribute("picture");
        String locale = oidcUser.getAttribute("locale");
        Boolean emailVerified = oidcUser.getAttribute("email_verified");

        logger.info("OIDC login for user: {}", email);

        UserProfile user = userApplicationService.saveOrUpdateOAuthUser(
                googleId,
                email,
                name,
                givenName,
                familyName,
                pictureUrl,
                locale,
                emailVerified != null && emailVerified
        );

        return new CustomOidcUser(oidcUser, user);
    }
}
