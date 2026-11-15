package com.vida_cruz.triage_service.infraestructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.infraestructure.entrypoints.security.ApiKeyAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "triage.security", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(SecurityProperties properties,
                                                                 ObjectMapper objectMapper) {
        log.info("API key authentication enabled ({} accepted key(s))", properties.apiKeys().size());
        return new ApiKeyAuthenticationFilter(properties.apiKeys(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "triage.security", name = "enabled", havingValue = "false")
    public ApplicationRunner securityDisabledWarning() {
        return args -> log.warn("API key authentication is DISABLED. Never run like this outside automated tests");
    }
}