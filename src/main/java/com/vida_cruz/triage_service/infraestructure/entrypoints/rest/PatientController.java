package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.domain.api.ChangePatientStatus;
import com.vida_cruz.triage_service.domain.api.GetPatient;
import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.ChangeStatusRequest;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientDetailResponse;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.PatientResponse;
import com.vida_cruz.triage_service.infraestructure.entrypoints.dto.RegisterPatientRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/triage/pacientes")
@RequiredArgsConstructor
public class PatientController {

    private final RegisterPatient registerPatient;
    private final ChangePatientStatus changePatientStatus;
    private final GetPatient getPatient;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        return registerPatient.register(request.toCommand())
                .map(PatientResponse::from);
    }

    @PatchMapping("/{id}/estado")
    public Mono<PatientResponse> changeStatus(@PathVariable UUID id,
                                              @Valid @RequestBody ChangeStatusRequest request) {
        return changePatientStatus.change(id, request.toStatus())
                .map(PatientResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<PatientDetailResponse> get(@PathVariable UUID id) {
        return getPatient.get(id)
                .map(PatientDetailResponse::from);
    }
}