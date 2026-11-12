package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

class FakeEventOutbox implements EventOutbox {

    final List<TriageEvent> events = new ArrayList<>();
    RuntimeException failure;

    @Override
    public Mono<Void> append(TriageEvent event) {
        return Mono.defer(() -> {
            if (failure != null) {
                return Mono.error(failure);
            }
            events.add(event);
            return Mono.empty();
        });
    }
}