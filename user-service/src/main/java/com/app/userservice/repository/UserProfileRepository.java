package com.app.userservice.repository;

import com.app.userservice.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

	Optional<UserProfile> findByGoogleId(String googleId);

	Optional<UserProfile> findByEmail(String email);

	boolean existsByEmail(String email);
}
