package com.heronix.guardian.config;

import com.heronix.guardian.security.HeronixEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Encrypted DataSource configuration for Heronix-Guardian.
 *
 * Only applies H2 CIPHER=AES encryption when the H2 profile is active.
 * PostgreSQL connections pass through unchanged.
 *
 * @author Heronix Educational Systems LLC
 * @since 2026-02
 */
@Slf4j
@Configuration
public class EncryptedDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HeronixEncryptionService enc = HeronixEncryptionService.getInstance();

        String url = properties.getUrl();
        String username = properties.getUsername();
        String userPassword = properties.getPassword() != null ? properties.getPassword() : "";
        String driverClassName = properties.getDriverClassName();

        if (enc.isDisabled() || url == null || !url.startsWith("jdbc:h2:file:")) {
            // PostgreSQL or disabled — pass through unchanged
            log.info("Using standard datasource (non-H2 or encryption disabled)");
            return DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(userPassword)
                    .driverClassName(driverClassName)
                    .build();
        }

        if (!url.contains("CIPHER=AES")) {
            url = url + ";CIPHER=AES";
        }

        String filePassword = enc.getH2FilePassword();
        String combinedPassword = filePassword + " " + userPassword;

        log.info("H2 database encryption enabled (CIPHER=AES)");

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(combinedPassword)
                .driverClassName(driverClassName)
                .build();
    }
}
