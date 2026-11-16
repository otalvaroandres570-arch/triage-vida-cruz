package com.vida_cruz.triage_service.infraestructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties(prefix = "triage.sla-monitor")
public record SlaMonitorProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10s") Duration interval,
        @DefaultValue("100") int batchSize
) {

    public SlaMonitorProperties {
        interval = Optional.ofNullable(interval)
                .filter(value -> !value.isZero() && !value.isNegative())
                .orElseThrow(() -> new IllegalArgumentException("triage.sla-monitor.interval must be positive"));
        batchSize = Optional.of(batchSize)
                .filter(value -> value > 0)
                .orElseThrow(() -> new IllegalArgumentException("triage.sla-monitor.batch-size must be positive"));
    }
}