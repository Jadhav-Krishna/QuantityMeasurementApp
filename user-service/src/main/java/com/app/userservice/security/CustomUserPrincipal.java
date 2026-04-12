package com.app.userservice.security;

import com.app.userservice.entity.UserProfile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class CustomUserPrincipal implements UserDetails, OAuth2User {

    private final UserProfile user;
    private final Map<String, Object> attributes;

    public CustomUserPrincipal(UserProfile user) {
        this(user, Map.of());
    }

    public CustomUserPrincipal(UserProfile user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = new HashMap<>(attributes);
    }

    public UserProfile getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String getName() {
        return user.getName() != null ? user.getName() : user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
