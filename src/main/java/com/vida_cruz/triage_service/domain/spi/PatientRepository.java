package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PatientRepository {

    /**
     * Persists a new patient together with its first audit entry.
     * Both writes must succeed or fail together (single transaction).
     */
    Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit);

    Mono<Patient> findById(UUID id);

    /**
     * Stores a status change together with its audit entry (single transaction).
     * The change is applied only if the stored status still equals {@code expectedStatus};
     * otherwise nothing is written and the result is empty, so a concurrent change is never overwritten.
     */
    Mono<Patient> updateStatusWithAudit(Patient updated, PatientStatus expectedStatus, PatientAudit audit);
}