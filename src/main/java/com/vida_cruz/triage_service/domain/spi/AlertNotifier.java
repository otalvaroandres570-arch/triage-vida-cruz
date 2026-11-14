package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import reactor.core.publisher.Mono;

public interface AlertNotifier {

    /** Completes when the channel accepted the alert; emits an error if it could not be delivered. */
    Mono<Void> notify(EscalationAlert alert);
}