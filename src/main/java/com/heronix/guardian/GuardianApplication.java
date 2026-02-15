package com.heronix.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.heronix.guardian.security.HeronixEncryptionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.heronix.guardian.config.GuardianProperties;

/**
 * Heronix Guardian - Zero-Trust Third-Party Integration Layer
 *
 * Guardian protects student privacy by tokenizing data before sending to
 * third-party vendors (Canvas, Google Classroom, etc.). Third-party applications
 * receive only anonymous tokens, never real PII.
 *
 * Core Principle: Third-party applications cannot expose data they never receive.
 */
@SpringBootApplication
@EnableConfigurationProperties(GuardianProperties.class)
@EnableAsync
@EnableScheduling
public class GuardianApplication {

    public static void main(String[] args) {
        HeronixEncryptionService.initialize();
        SpringApplication.run(GuardianApplication.class, args);
    }
}
