package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface EscalationRepository {

    /**
     * Patients still waiting (REGISTRADO), not escalated, whose SLA expired strictly before {@code now},
     * oldest registration first, at most {@code limit}.
     */
    Flux<Patient> findOverdue(Instant now, int limit);

    /**
     * Sets the escalation flag and stores the audit entry in one transaction.
     * It only applies if the patient is still REGISTRADO and not escalated; otherwise nothing is
     * written and the result is empty. This is what makes escalation idempotent (RN-03),
     * even with concurrent callers.
     */
    Mono<Patient> markEscalated(Patient escalated, PatientAudit audit);
}