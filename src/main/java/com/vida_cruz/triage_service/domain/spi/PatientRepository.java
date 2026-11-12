package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PatientRepository {

    /**
     * Persists a new patient together with its first audit entry.
     * Both writes must succeed or fail together (single transaction).
     */
    Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit);

    Mono<Patient> findById(UUID id);
}