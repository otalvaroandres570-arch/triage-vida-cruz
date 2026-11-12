package com.vida_cruz.triage_service.domain.api;

import com.vida_cruz.triage_service.domain.model.PatientDetails;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetPatient {

    Mono<PatientDetails> get(UUID patientId);
}