package com.cove.server.auth;

import com.cove.server.user.User;
import com.cove.server.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/request-otp")
    public void requestOtp(@Valid @RequestBody OtpRequest request) {
        authService.requestOtp(request.channel(), request.identifier());
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        var result = authService.verifyOtp(
            request.channel(),
            request.identifier(),
            request.code(),
            request.username(),
            request.displayName(),
            request.identityKey()
        );
        return new AuthResponse(result.token(), result.isNewUser(), UserResponse.from(result.user()));
    }

    @GetMapping("/me")
    public UserResponse me() {
        UUID userId = AuthContext.requireUserId();
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        userService.updateLastSeen(userId);
        return UserResponse.from(user);
    }

    public record OtpRequest(@NotBlank String channel, @NotBlank String identifier) {}
    public record OtpVerifyRequest(@NotBlank String channel, @NotBlank String identifier, @NotBlank String code,
                                   String username, String displayName, String identityKey) {}
    public record AuthResponse(String token, boolean isNewUser, UserResponse user) {}

    public record UserResponse(UUID id, String username, String email, String phone, String displayName,
                               String avatarUrl, String identityKey) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getDisplayName(), user.getAvatarUrl(), user.getIdentityKey());
        }
    }
}
