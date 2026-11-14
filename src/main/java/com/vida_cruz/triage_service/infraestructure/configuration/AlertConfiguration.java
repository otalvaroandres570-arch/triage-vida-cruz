package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.DispatchAlerts;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import com.vida_cruz.triage_service.domain.usecase.DispatchAlertsUseCase;
import com.vida_cruz.triage_service.infraestructure.adapters.alerting.LoggingAlertNotifier;
import com.vida_cruz.triage_service.infraestructure.adapters.alerting.WebhookAlertNotifier;
import com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler.PeriodicJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.util.Optional;

@Slf4j
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfiguration {

    @Bean
    public AlertNotifier alertNotifier(AlertProperties properties) {
        log.info("Escalation alerts go to {}",
                properties.webhookUrl() == null ? "the log only (no webhook configured)" : "the configured webhook");

        return Optional.ofNullable(properties.webhookUrl())
                .<AlertNotifier>map(url -> new WebhookAlertNotifier(
                        WebClient.builder().build(), url, properties.webhookTimeout()))
                .orElseGet(LoggingAlertNotifier::new);
    }

    @Bean
    public DispatchAlerts dispatchAlerts(AlertQueue queue, AlertNotifier notifier, Clock clock,
                                         AlertProperties properties) {
        return new DispatchAlertsUseCase(queue, notifier, clock,
                properties.batchSize(), properties.lease(), properties.backoff());
    }

    @Bean
    @ConditionalOnProperty(prefix = "triage.alerts", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PeriodicJob alertDispatcherJob(DispatchAlerts dispatchAlerts, AlertProperties properties) {
        return new PeriodicJob("Alert dispatcher", properties.interval(), dispatchAlerts::dispatchDue);
    }
}