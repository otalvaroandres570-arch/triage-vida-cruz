package com.vida_cruz.triage_service.domain.exceptions;

import com.vida_cruz.triage_service.domain.model.TriageEvent;

public class EventPublishingException extends RuntimeException {
    public EventPublishingException(TriageEvent event, Throwable cause) {
        super("No se pudo publicar el evento %s del paciente %s".formatted(event.type(), event.patientId()), cause);
    }
}
