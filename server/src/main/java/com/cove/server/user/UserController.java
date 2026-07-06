package com.cove.server.user;

import com.cove.server.auth.AuthContext;
import com.cove.server.auth.AuthController;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @PutMapping("/me")
    public AuthController.UserResponse updateMe(@RequestBody UpdateProfileRequest request) {
        UUID userId = AuthContext.requireUserId();
        User user = userService.updateProfile(userId, request.username(), request.displayName());
        return AuthController.UserResponse.from(user);
    }

    @GetMapping("/search")
    public List<UserSummary> search(@RequestParam("q") @NotBlank String query) {
        return userRepository.search(query).stream().map(UserSummary::from).collect(Collectors.toList());
    }

    @GetMapping("/keys")
    public List<UserKey> keys(@RequestParam("ids") List<UUID> ids) {
        return userRepository.findByIds(ids).stream().map(UserKey::from).collect(Collectors.toList());
    }

    public record UpdateProfileRequest(String username, String displayName) {}

    public record UserSummary(UUID id, String username, String displayName, String email, String phone, String avatarUrl) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getPhone(), user.getAvatarUrl());
        }
    }

    public record UserKey(UUID id, String identityKey, String displayName) {
        public static UserKey from(User user) {
            return new UserKey(user.getId(), user.getIdentityKey(), user.getDisplayName());
        }
    }
}
