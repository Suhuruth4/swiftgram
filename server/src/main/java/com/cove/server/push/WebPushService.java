package com.cove.server.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cove.server.config.AppProperties;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebPushService {
    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private final PushSubscriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;
    private final boolean enabled;

    public WebPushService(PushSubscriptionRepository repository, ObjectMapper objectMapper, AppProperties props) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        PushService svc = null;
        boolean ok = false;
        try {
            String pub = props.getPush().getVapidPublicKey();
            String priv = props.getPush().getVapidPrivateKey();
            if (pub != null && !pub.isBlank() && priv != null && !priv.isBlank()) {
                svc = new PushService();
                svc.setPublicKey(Utils.loadPublicKey(pub));
                svc.setPrivateKey(Utils.loadPrivateKey(priv));
                svc.setSubject(props.getPush().getSubject());
                ok = true;
            }
        } catch (Exception ex) {
            log.warn("Web push disabled: invalid VAPID keys", ex);
        }
        this.pushService = svc;
        this.enabled = ok;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void sendToUser(UUID userId, Map<String, Object> payload) {
        if (!enabled) return;
        List<PushSubscription> subs = repository.findByUserId(userId);
        for (PushSubscription sub : subs) {
            try {
                Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)
                );
                pushService.send(notification);
            } catch (Exception ex) {
                log.warn("Failed to send web push", ex);
            }
        }
    }
}
