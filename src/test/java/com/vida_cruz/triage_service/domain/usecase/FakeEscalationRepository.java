package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.spi.EscalationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory double that mimics the contract of the real repository, including its conditional update. */
class FakeEscalationRepository implements EscalationRepository {

    final Map<UUID, Patient> patients = new LinkedHashMap<>();
    final List<PatientAudit> audits = new ArrayList<>();
    UUID failOnPatient;

    Patient seed(Patient patient) {
        patients.put(patient.id(), patient);
        return patient;
    }

    @Override
    public Flux<Patient> findOverdue(Instant now, int limit) {
        return Flux.defer(() -> Flux.fromIterable(patients.values().stream()
                .filter(patient -> patient.canBeEscalated(now))
                .sorted(Comparator.comparing(Patient::registeredAt))
                .limit(limit)
                .toList()));
    }

    @Override
    public Mono<Patient> markEscalated(Patient escalated, PatientAudit audit) {
        return Mono.defer(() -> escalated.id().equals(failOnPatient)
                ? Mono.error(new IllegalStateException("db failure for " + escalated.id()))
                : Mono.justOrEmpty(Optional.ofNullable(patients.get(escalated.id()))
                .filter(stored -> stored.status() == PatientStatus.REGISTRADO && !stored.escalated())
                .map(stored -> store(escalated, audit))));
    }

    private Patient store(Patient escalated, PatientAudit audit) {
        patients.put(escalated.id(), escalated);
        audits.add(audit);
        return escalated;
    }
}