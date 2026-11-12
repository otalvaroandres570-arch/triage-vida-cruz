package com.vida_cruz.triage_service.domain.api;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangePatientStatus {

    Mono<Patient> change(UUID patientId, PatientStatus target);
}