package com.app.userservice.security;

import com.app.userservice.entity.UserProfile;
import com.app.userservice.service.UserApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserApplicationService userApplicationService;

    public CustomOAuth2UserService(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String googleId = oauth2User.getAttribute("sub");
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String givenName = oauth2User.getAttribute("given_name");
        String familyName = oauth2User.getAttribute("family_name");
        String pictureUrl = oauth2User.getAttribute("picture");
        String locale = oauth2User.getAttribute("locale");
        Boolean emailVerified = oauth2User.getAttribute("email_verified");

        logger.info("OAuth2 login for user: {}", email);

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

        return new CustomOAuth2User(oauth2User, user);
    }
}
