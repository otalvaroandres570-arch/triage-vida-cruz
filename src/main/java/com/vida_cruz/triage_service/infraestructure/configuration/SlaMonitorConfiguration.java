package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.EscalateOverduePatients;
import com.vida_cruz.triage_service.domain.spi.*;
import com.vida_cruz.triage_service.domain.usecase.EscalateOverduePatientsUseCase;
import com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler.PeriodicJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SlaMonitorProperties.class)
public class SlaMonitorConfiguration {


    @Bean
    public EscalateOverduePatients escalateOverduePatients(EscalationRepository repository,
                                                           EventOutbox outbox,
                                                           AlertQueue alertQueue,
                                                           TransactionRunner transaction,
                                                           TriageMetrics metrics,
                                                           Clock clock,
                                                           SlaMonitorProperties properties) {
        return new EscalateOverduePatientsUseCase(repository, outbox, alertQueue, transaction, metrics, clock,
                properties.batchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "triage.sla-monitor", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public PeriodicJob slaMonitorJob(EscalateOverduePatients escalateOverduePatients,
                                     SlaMonitorProperties properties) {
        return new PeriodicJob("SLA monitor", properties.interval(), escalateOverduePatients::escalateOverdue);
    }
}