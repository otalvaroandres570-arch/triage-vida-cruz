package com.vida_cruz.triage_service.infraestructure.adapters.alerting;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j(topic = "escalation-alerts")
public class LoggingAlertNotifier implements AlertNotifier {

    @Override
    public Mono<Void> notify(EscalationAlert alert) {
        return Mono.fromRunnable(() -> log.warn("ESCALATION ALERT: {}", alert.message()));
    }
}