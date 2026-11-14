package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.DispatchAlerts;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import com.vida_cruz.triage_service.domain.spi.AlertQueue;
import com.vida_cruz.triage_service.domain.usecase.DispatchAlertsUseCase;
import com.vida_cruz.triage_service.infraestructure.adapters.alerting.LoggingAlertNotifier;
import com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler.PeriodicJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfiguration {

    @Bean
    public AlertNotifier alertNotifier() {
        return new LoggingAlertNotifier();
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