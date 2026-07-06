package com.cove.server.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,20}$");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    @Transactional
    public User createOrUpdateEmailUser(String email, String username, String displayName, String identityKey) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        if (user.getId() == null) {
            user.setEmail(email);
            user.setUsername(claimUsername(username, null));
            user.setCreatedAt(Instant.now());
        }
        applyProfile(user, displayName, identityKey);
        return userRepository.save(user);
    }

    @Transactional
    public User createOrUpdatePhoneUser(String phone, String username, String displayName, String identityKey) {
        User user = userRepository.findByPhone(phone).orElseGet(User::new);
        if (user.getId() == null) {
            user.setPhone(phone);
            user.setUsername(claimUsername(username, null));
            user.setCreatedAt(Instant.now());
        }
        applyProfile(user, displayName, identityKey);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(UUID userId, String username, String displayName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (username != null && !username.isBlank()) {
            String normalized = username.trim().toLowerCase();
            if (!normalized.equals(user.getUsername())) {
                user.setUsername(claimUsername(normalized, userId));
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        return userRepository.save(user);
    }

    public void updateLastSeen(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
        });
    }

    private void applyProfile(User user, String displayName, String identityKey) {
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (identityKey != null && !identityKey.isBlank()) {
            user.setIdentityKey(identityKey);
        }
        user.setLastSeenAt(Instant.now());
    }

    private String claimUsername(String requested, UUID currentUserId) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = requested.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "Username must be 3-20 characters: lowercase letters, numbers and underscores");
        }
        Optional<User> existing = userRepository.findByUsername(normalized);
        if (existing.isPresent() && !existing.get().getId().equals(currentUserId)) {
            throw new IllegalStateException("That username is already taken");
        }
        return normalized;
    }
}
