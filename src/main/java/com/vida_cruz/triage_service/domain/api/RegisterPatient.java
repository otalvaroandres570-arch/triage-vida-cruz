package com.vida_cruz.triage_service.domain.api;

import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import reactor.core.publisher.Mono;

public interface RegisterPatient {

    Mono<Patient> register(RegisterPatientCommand command);
}