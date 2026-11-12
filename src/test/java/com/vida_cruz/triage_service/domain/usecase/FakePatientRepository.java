package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory repository that mimics the contract of the real one,
 * including the "expected status" guard of updateStatusWithAudit.
 */
class FakePatientRepository implements PatientRepository {

    final Map<UUID, Patient> patients = new HashMap<>();
    final List<PatientAudit> audits = new ArrayList<>();
    boolean rejectUpdatesAsStale;

    Patient seed(Patient patient) {
        patients.put(patient.id(), patient);
        return patient;
    }

    @Override
    public Mono<Patient> saveWithAudit(Patient patient, PatientAudit audit) {
        patients.put(patient.id(), patient);
        audits.add(audit);
        return Mono.just(patient);
    }

    @Override
    public Mono<Patient> findById(UUID id) {
        return Mono.defer(() -> Mono.justOrEmpty(patients.get(id)));
    }

    @Override
    public Mono<Patient> updateStatusWithAudit(Patient updated, PatientStatus expectedStatus, PatientAudit audit) {
        return Mono.defer(() -> {
            Patient stored = patients.get(updated.id());
            if (rejectUpdatesAsStale || stored == null || stored.status() != expectedStatus) {
                return Mono.empty();
            }
            patients.put(updated.id(), updated);
            audits.add(audit);
            return Mono.just(updated);
        });
    }
}