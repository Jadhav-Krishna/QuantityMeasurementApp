package com.app.userservice.service;

import com.app.userservice.client.EmailServiceClient;
import com.app.userservice.dto.CreditExhaustedEmailRequest;
import com.app.userservice.dto.CreateUserRequest;
import com.app.userservice.dto.LoginEmailRequest;
import com.app.userservice.dto.SignupEmailRequest;
import com.app.userservice.dto.UserHistoryRequest;
import com.app.userservice.dto.UserHistoryResponse;
import com.app.userservice.dto.UserResponse;
import com.app.userservice.entity.UserHistoryRecord;
import com.app.userservice.entity.UserProfile;
import com.app.userservice.repository.UserHistoryRepository;
import com.app.userservice.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserApplicationService.class);

    private final UserProfileRepository userProfileRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailServiceClient emailServiceClient;

    public UserApplicationService(
            UserProfileRepository userProfileRepository,
            UserHistoryRepository userHistoryRepository,
            PasswordEncoder passwordEncoder,
            EmailServiceClient emailServiceClient
    ) {
        this.userProfileRepository = userProfileRepository;
        this.userHistoryRepository = userHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailServiceClient = emailServiceClient;
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userProfileRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("User already exists with email: " + request.email());
        }
        UserProfile profile = new UserProfile();
        profile.setName(request.name());
        profile.setEmail(request.email());
        profile.setPassword(passwordEncoder.encode("change-me"));
        profile.setGivenName(request.name());
        profile.setAuthProvider(UserProfile.AuthProvider.LOCAL.name());
        profile.setEnabled(true);
        profile.setCreatedAt(Instant.now());
        return toUserResponse(userProfileRepository.save(profile));
    }

    public UserProfile saveOrUpdateOAuthUser(
            String googleId,
            String email,
            String name,
            String givenName,
            String familyName,
            String pictureUrl,
            String locale,
            boolean emailVerified
    ) {
        Optional<UserProfile> existingUser = userProfileRepository.findByGoogleId(googleId);
        if (existingUser.isEmpty()) {
            existingUser = userProfileRepository.findByEmail(email);
        }

        UserProfile user = existingUser.orElseGet(UserProfile::new);
        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setName(name);
        user.setGivenName(givenName);
        user.setFamilyName(familyName);
        user.setPictureUrl(pictureUrl);
        user.setLocale(locale);
        user.setEmailVerified(emailVerified);
        user.setAuthProvider(user.getPassword() != null ? UserProfile.AuthProvider.HYBRID.name() : UserProfile.AuthProvider.GOOGLE.name());
        user.setEnabled(true);
        user.setLastLoginAt(Instant.now());

        if (user.getRole() == null) {
            user.setRole(UserProfile.Role.USER.name());
        }

        UserProfile saved = userProfileRepository.save(user);

        sendLoginEmail(saved);

        return saved;
    }

    public UserProfile registerLocalUser(String name, String email, String rawPassword) {
        Optional<UserProfile> existingUser = userProfileRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            UserProfile user = existingUser.get();
            if (user.getPassword() != null) {
                throw new IllegalArgumentException("User already exists with email: " + email);
            }

            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setName(name);
            user.setGivenName(name);
            user.setAuthProvider(UserProfile.AuthProvider.HYBRID.name());
            user.setLastLoginAt(Instant.now());
            user.setEnabled(true);
            UserProfile saved = userProfileRepository.save(user);
            sendSignupEmail(saved);
            return saved;
        }

        UserProfile profile = new UserProfile();
        profile.setName(name);
        profile.setEmail(email);
        profile.setPassword(passwordEncoder.encode(rawPassword));
        profile.setGivenName(name);
        profile.setAuthProvider(UserProfile.AuthProvider.LOCAL.name());
        profile.setRole(UserProfile.Role.USER.name());
        profile.setEnabled(true);
        profile.setLastLoginAt(Instant.now());
        UserProfile saved = userProfileRepository.save(profile);
        sendSignupEmail(saved);
        return saved;
    }

    public Optional<UserProfile> findByGoogleId(String googleId) {
        return userProfileRepository.findByGoogleId(googleId);
    }

    public Optional<UserProfile> findByEmail(String email) {
        return userProfileRepository.findByEmail(email);
    }

    public UserProfile updateLastLogin(UserProfile user) {
        user.setLastLoginAt(Instant.now());
        UserProfile saved = userProfileRepository.save(user);
        sendLoginEmail(saved);
        return saved;
    }

    public UserResponse getUser(Long userId) {
        return toUserResponse(userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId)));
    }

    public UserHistoryResponse addHistory(Long userId, UserHistoryRequest request) {
        ensureUserExists(userId);
        UserHistoryRecord record = new UserHistoryRecord();
        record.setUserId(userId);
        record.setCategory(request.category());
        record.setOperation(request.operation());
        record.setInputValue(request.inputValue());
        record.setInputUnit(request.inputUnit());
        record.setSecondaryValue(request.secondaryValue());
        record.setSecondaryUnit(request.secondaryUnit());
        record.setResultValue(request.resultValue());
        record.setResultUnit(request.resultUnit());
        record.setComparisonResult(request.comparisonResult());
        record.setMessage(request.message());
        record.setRecordedAt(Instant.now());
        return toHistoryResponse(userHistoryRepository.save(record));
    }

    public List<UserHistoryResponse> getHistory(Long userId) {
        ensureUserExists(userId);
        return userHistoryRepository.findByUserIdOrderByRecordedAtDesc(userId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public void deleteHistory(Long userId, Long historyId) {
        ensureUserExists(userId);
        UserHistoryRecord record = userHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("History record not found: " + historyId));
        userHistoryRepository.delete(record);
    }

    private void ensureUserExists(Long userId) {
        if (!userProfileRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
    }

    public int getCredits(Long userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId))
                .getCredits();
    }

    public int addCredits(Long userId, int amount) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setCredits(user.getCredits() + amount);
        return userProfileRepository.save(user).getCredits();
    }

    public int deductCredit(Long userId) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getCredits() <= 0) {
            try {
                emailServiceClient.sendCreditExhaustedEmail(
                        new CreditExhaustedEmailRequest(user.getEmail(), user.getName()));
            } catch (Exception e) {
                log.warn("Failed to send credit exhausted email for user {}: {}", user.getId(), e.getMessage());
            }
            throw new IllegalStateException("No credits remaining. Please recharge.");
        }
        user.setCredits(user.getCredits() - 1);
        return userProfileRepository.save(user).getCredits();
    }

    public UserResponse toUserResponse(UserProfile profile) {
        return new UserResponse(
                profile.getId(),
                profile.getName(),
                profile.getEmail(),
                profile.getGivenName(),
                profile.getFamilyName(),
                profile.getPictureUrl(),
                profile.isEmailVerified(),
                profile.getRole(),
                profile.getAuthProvider(),
                profile.getCredits(),
                profile.getCreatedAt(),
                profile.getLastLoginAt()
        );
    }

    private UserHistoryResponse toHistoryResponse(UserHistoryRecord record) {
        return new UserHistoryResponse(
                record.getId(),
                record.getUserId(),
                record.getCategory(),
                record.getOperation(),
                record.getInputValue(),
                record.getInputUnit(),
                record.getSecondaryValue(),
                record.getSecondaryUnit(),
                record.getResultValue(),
                record.getResultUnit(),
                record.getComparisonResult(),
                record.getMessage(),
                record.getRecordedAt()
        );
    }

    private void sendLoginEmail(UserProfile user) {
        try {
            emailServiceClient.sendLoginEmail(new LoginEmailRequest(user.getEmail(), user.getName()));
        } catch (Exception e) {
            log.warn("Failed to send login email for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private void sendSignupEmail(UserProfile user) {
        try {
            emailServiceClient.sendSignupEmail(new SignupEmailRequest(user.getEmail(), user.getName()));
        } catch (Exception e) {
            log.warn("Failed to send signup email for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
