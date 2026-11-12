package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import com.vida_cruz.triage_service.domain.usecase.PublishPendingEventsUseCase;
import com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler.OutboxRelayScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxConfiguration {

    @Bean
    public PublishPendingEvents publishPendingEvents(PendingEventStore store,
                                                     EventPublisher publisher,
                                                     OutboxRelayProperties properties,
                                                     Clock clock) {
        return new PublishPendingEventsUseCase(store, publisher, clock, properties.batchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "triage.outbox.relay", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(PublishPendingEvents publishPendingEvents,
                                                     OutboxRelayProperties properties) {
        return new OutboxRelayScheduler(publishPendingEvents, properties.interval());
    }
}