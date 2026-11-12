package com.vida_cruz.triage_service.infraestructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "triage.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1s") Duration interval,
        @DefaultValue("100") int batchSize
) {

    public OutboxRelayProperties {
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("triage.outbox.relay.interval must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("triage.outbox.relay.batch-size must be positive");
        }
    }
}