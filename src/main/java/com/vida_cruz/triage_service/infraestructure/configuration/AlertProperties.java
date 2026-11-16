package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.model.RetryBackoff;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties(prefix = "triage.alerts")
public record AlertProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5s") Duration interval,
        @DefaultValue("20") int batchSize,
        @DefaultValue("60s") Duration lease,
        @DefaultValue("5s") Duration retryBase,
        @DefaultValue("5m") Duration retryMax,
        String webhookUrl,
        @DefaultValue("5s") Duration webhookTimeout
) {

    public AlertProperties {
        interval = positive(interval, "interval");
        lease = positive(lease, "lease");
        retryBase = positive(retryBase, "retry-base");
        retryMax = positive(retryMax, "retry-max");
        webhookTimeout = positive(webhookTimeout, "webhook-timeout");
        batchSize = Optional.of(batchSize)
                .filter(value -> value > 0)
                .orElseThrow(() -> new IllegalArgumentException("triage.alerts.batch-size must be positive"));
        // the URL is a secret: validated without ever echoing it
        webhookUrl = Optional.ofNullable(webhookUrl)
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .map(url -> Optional.of(url)
                        .filter(candidate -> candidate.startsWith("http://") || candidate.startsWith("https://"))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "triage.alerts.webhook-url must start with http:// or https://")))
                .orElse(null);
    }

    public RetryBackoff backoff() {
        return new RetryBackoff(retryBase, retryMax);
    }

    private static Duration positive(Duration value, String name) {
        return Optional.ofNullable(value)
                .filter(duration -> !duration.isZero() && !duration.isNegative())
                .orElseThrow(() -> new IllegalArgumentException("triage.alerts." + name + " must be positive"));
    }
}