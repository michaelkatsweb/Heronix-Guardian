package com.heronix.guardian.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.heronix.guardian.service.SisTokenBridgeService;

/**
 * Spring Boot Actuator health indicator for SIS integration.
 *
 * Reports UP when SIS-Server is reachable and tokenization is available.
 * Reports DOWN when SIS-Server is unreachable (Guardian falls back to local tokens).
 *
 * Only active when SisTokenBridgeService is loaded (use-sis-tokenization=true).
 */
@Component
public class SisHealthIndicator implements HealthIndicator {

    @Autowired(required = false)
    private SisTokenBridgeService sisTokenBridge;

    @Override
    public Health health() {
        if (sisTokenBridge == null) {
            return Health.up()
                    .withDetail("mode", "standalone")
                    .withDetail("sis-tokenization", "disabled")
                    .build();
        }

        boolean available = sisTokenBridge.isSisAvailable();

        if (available) {
            return Health.up()
                    .withDetail("mode", "integrated")
                    .withDetail("sis-tokenization", "connected")
                    .build();
        } else {
            return Health.down()
                    .withDetail("mode", "integrated")
                    .withDetail("sis-tokenization", "unreachable")
                    .withDetail("fallback", "local-tokens")
                    .build();
        }
    }
}
