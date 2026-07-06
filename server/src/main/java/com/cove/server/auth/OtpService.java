package com.cove.server.auth;

import com.cove.server.config.AppProperties;
import com.cove.server.notification.NotificationService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {
    private final StringRedisTemplate redisTemplate;
    private final AppProperties props;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate, AppProperties props, NotificationService notificationService) {
        this.redisTemplate = redisTemplate;
        this.props = props;
        this.notificationService = notificationService;
    }

    public void requestOtp(String channel, String identifier) {
        String code = generateCode(props.getOtp().getLength());
        String key = buildKey(channel, identifier);
        redisTemplate.opsForValue().set(key, code, Duration.ofSeconds(props.getOtp().getTtlSeconds()));
        if ("email".equalsIgnoreCase(channel)) {
            notificationService.sendEmailOtp(identifier, code);
        } else if ("phone".equalsIgnoreCase(channel)) {
            notificationService.sendSmsOtp(identifier, code);
        } else {
            throw new IllegalArgumentException("Unsupported channel");
        }
    }

    public boolean verifyOtp(String channel, String identifier, String code) {
        String key = buildKey(channel, identifier);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String buildKey(String channel, String identifier) {
        return "otp:" + channel + ":" + identifier.toLowerCase();
    }
}
