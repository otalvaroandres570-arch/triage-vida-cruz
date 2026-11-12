package com.vida_cruz.triage_service.infraestructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@ConfigurationProperties(prefix = "triage.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1s") Duration interval,
        @DefaultValue("100") int batchSize
) {

    public OutboxRelayProperties {
        interval = Optional.of(Objects.requireNonNull(interval, "interval must not be null"))
                .filter(Duration::isPositive)
                .orElseThrow(() -> new IllegalArgumentException("triage.outbox.relay.interval must be positive"));
        batchSize = Optional.of(batchSize)
                .filter(size -> size > 0)
                .orElseThrow(() -> new IllegalArgumentException("triage.outbox.relay.batch-size must be positive"));
    }
}