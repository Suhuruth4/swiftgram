package com.cove.server.push;

import com.cove.server.auth.AuthContext;
import com.cove.server.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/push")
public class PushController {
    private final PushSubscriptionRepository repository;
    private final AppProperties props;

    public PushController(PushSubscriptionRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @GetMapping("/vapid-public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", props.getPush().getVapidPublicKey() == null ? "" : props.getPush().getVapidPublicKey());
    }

    @PostMapping("/subscribe")
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        UUID userId = AuthContext.requireUserId();
        PushSubscription sub = repository.findByEndpoint(request.endpoint()).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(request.endpoint());
        sub.setP256dh(request.keys().p256dh());
        sub.setAuth(request.keys().auth());
        repository.save(sub);
    }

    @DeleteMapping("/unsubscribe")
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        repository.findByEndpoint(request.endpoint()).ifPresent(repository::delete);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PushSubscriptionRequest(@NotBlank String endpoint, @Valid Keys keys) {
        public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {}
}
