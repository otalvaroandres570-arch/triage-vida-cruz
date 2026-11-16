package com.vida_cruz.triage_service.infraestructure.entrypoints.error;

import com.vida_cruz.triage_service.domain.exceptions.InvalidStatusTransitionException;
import com.vida_cruz.triage_service.domain.exceptions.PatientNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

class GlobalExceptionHandlerTest {

    private final WebTestClient client = WebTestClient
            .bindToController(new StubController())
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldReturn404WhenPatientNotFound() {
        client.get().uri("/stub/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("PATIENT_NOT_FOUND");
    }

    @Test
    void shouldReturn409WhenStatusTransitionIsInvalid() {
        client.get().uri("/stub/conflict")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION")
                .jsonPath("$.message").value(message ->
                        org.assertj.core.api.Assertions.assertThat(message.toString())
                                .contains("ALTA", "EN_CONSULTA"));
    }

    @Test
    void shouldReturn400WithFieldNameWhenValidationFails() {
        client.post().uri("/stub/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\": \"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.violations[0].field").isEqualTo("name");
    }

    @Test
    void shouldReturn400WhenBodyIsMalformed() {
        client.post().uri("/stub/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{ not json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void shouldReturn500WithoutLeakingInternalDetails() {
        client.get().uri("/stub/boom")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").value(message ->
                        org.assertj.core.api.Assertions.assertThat(message.toString())
                                .doesNotContain("secret internal detail"));
    }

    @RestController
    @RequestMapping("/stub")
    static class StubController {

        @Data
        static class Body {
            @NotBlank(message = "es obligatorio")
            private String name;
        }

        @GetMapping("/not-found")
        Mono<String> notFound() {
            return Mono.error(new PatientNotFoundException(UUID.randomUUID()));
        }

        @GetMapping("/conflict")
        Mono<String> conflict() {
            return Mono.error(new InvalidStatusTransitionException("ALTA", "EN_CONSULTA"));
        }

        @GetMapping("/boom")
        Mono<String> boom() {
            return Mono.error(new IllegalStateException("secret internal detail"));
        }

        @PostMapping("/validate")
        Mono<String> validate(@Valid @RequestBody Body body) {
            return Mono.just("ok");
        }
    }
}