package com.cove.server.auth;

import com.cove.server.notification.NotificationService;
import com.cove.server.user.User;
import com.cove.server.user.UserService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final OtpService otpService;
    private final UserService userService;
    private final JwtService jwtService;
    private final NotificationService notificationService;

    public AuthService(OtpService otpService, UserService userService, JwtService jwtService, NotificationService notificationService) {
        this.otpService = otpService;
        this.userService = userService;
        this.jwtService = jwtService;
        this.notificationService = notificationService;
    }

    public void requestOtp(String channel, String identifier) {
        otpService.requestOtp(channel, identifier);
    }

    public AuthResult verifyOtp(String channel, String identifier, String code, String username, String displayName, String identityKey) {
        boolean valid = otpService.verifyOtp(channel, identifier, code);
        if (!valid) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        User user;
        boolean created;
        if ("email".equalsIgnoreCase(channel)) {
            created = userService.findByEmail(identifier).isEmpty();
            user = userService.createOrUpdateEmailUser(identifier, username, displayName, identityKey);
            if (created) {
                notificationService.sendWelcomeEmail(identifier);
            }
        } else if ("phone".equalsIgnoreCase(channel)) {
            created = userService.findByPhone(identifier).isEmpty();
            user = userService.createOrUpdatePhoneUser(identifier, username, displayName, identityKey);
        } else {
            throw new IllegalArgumentException("Unsupported channel");
        }
        String token = jwtService.generateToken(user);
        return new AuthResult(user, token, created);
    }

    public record AuthResult(User user, String token, boolean isNewUser) {}
}
