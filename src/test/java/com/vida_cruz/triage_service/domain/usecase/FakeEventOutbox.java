package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class FakeEventOutbox implements EventOutbox {

    final List<TriageEvent> events = new ArrayList<>();
    RuntimeException failure;

    @Override
    public Mono<Void> append(TriageEvent event) {
        return Mono.defer(() -> Optional.ofNullable(failure)
                .<Mono<Void>>map(Mono::error)
                .orElseGet(() -> Mono.fromRunnable(() -> events.add(event))));
    }
}