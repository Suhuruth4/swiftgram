package com.swiftgram.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, Object> get() {
        return Map.of("ok", true, "ts", Instant.now().toString());
    }
}
