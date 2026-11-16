package com.vida_cruz.triage_service.infraestructure.entrypoints.rest;

import com.vida_cruz.triage_service.domain.api.GenerateOperationReport;
import com.vida_cruz.triage_service.domain.model.HourlyOperation;
import com.vida_cruz.triage_service.domain.model.OperationReport;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.infraestructure.entrypoints.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(ReportController.class)
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

    private static final String VALID_RANGE = "desde=2026-11-13T00:00:00Z&hasta=2026-11-14T00:00:00Z";

    @Autowired
    private WebTestClient client;

    @MockBean
    private GenerateOperationReport generateOperationReport;

    @Test
    void shouldReturnTheHourlyReport() {
        ReportRange range = new ReportRange(Instant.parse("2026-11-13T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
        Map<TriageLevel, Long> levels = Map.of(TriageLevel.CRITICO, 3L, TriageLevel.URGENTE, 1L);
        when(generateOperationReport.generate(any(ReportRange.class))).thenReturn(Mono.just(new OperationReport(range, List.of(
                new HourlyOperation(Instant.parse("2026-11-13T10:00:00Z"), 5, levels, 3, 21.67, 33.5),
                new HourlyOperation(Instant.parse("2026-11-13T13:00:00Z"), 1, Map.of(), 0, null, null)))));

        client.get().uri("/triage/reportes/operacion?" + VALID_RANGE).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.desde").isEqualTo("2026-11-13T00:00:00Z")
                .jsonPath("$.horas[0].hora").isEqualTo("2026-11-13T10:00:00Z")
                .jsonPath("$.horas[0].totalRegistrados").isEqualTo(5)
                .jsonPath("$.horas[0].porNivel.CRITICO").isEqualTo(3)
                .jsonPath("$.horas[0].porNivel.URGENTE_2").isEqualTo(0)
                .jsonPath("$.horas[0].atendidos").isEqualTo(3)
                .jsonPath("$.horas[0].esperaPromedioMinutos").isEqualTo(21.67)
                .jsonPath("$.horas[0].esperaP95Minutos").isEqualTo(33.5)
                .jsonPath("$.horas[1].atendidos").isEqualTo(0)
                .jsonPath("$.horas[1].esperaPromedioMinutos").isEmpty();
    }

    @ParameterizedTest(name = "{0} is rejected with 400")
    @ValueSource(strings = {
            "hasta=2026-11-14T00:00:00Z",
            "desde=ayer&hasta=2026-11-14T00:00:00Z",
            "desde=2026-11-14T00:00:00Z&hasta=2026-11-13T00:00:00Z",
            "desde=2026-01-01T00:00:00Z&hasta=2026-12-31T00:00:00Z"
    })
    void shouldRejectMissingMalformedOrInvalidRanges(String query) {
        client.get().uri("/triage/reportes/operacion?" + query).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REPORT_RANGE");
    }
}