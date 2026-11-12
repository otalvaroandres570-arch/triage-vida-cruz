package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.RegisterPatientRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/triage/pacientes")
@RequiredArgsConstructor
public class PatientController {

    private final RegisterPatient registerPatient;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        return registerPatient.register(request.toCommand())
                .map(PatientResponse::from);
    }
}