package com.vida_cruz.triage_service.infraestructure.entrypoints.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vida_cruz.triage_service.domain.model.HourlyOperation;
import com.vida_cruz.triage_service.domain.model.OperationReport;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record OperationReportResponse(
        @JsonProperty("desde") Instant from,
        @JsonProperty("hasta") Instant to,
        @JsonProperty("horas") List<Hour> hours
) {

    public record Hour(
            @JsonProperty("hora") Instant hour,
            @JsonProperty("totalRegistrados") long total,
            @JsonProperty("porNivel") Map<String, Long> byLevel,
            @JsonProperty("atendidos") long attended,
            @JsonProperty("esperaPromedioMinutos") Double averageWaitMinutes,
            @JsonProperty("esperaP95Minutos") Double p95WaitMinutes
    ) {

        static Hour from(HourlyOperation operation) {
            Map<String, Long> byLevel = Arrays.stream(TriageLevel.values())
                    .collect(Collectors.toMap(
                            TriageLevel::name,
                            level -> operation.byLevel().getOrDefault(level, 0L),
                            (first, second) -> first,
                            LinkedHashMap::new)); // levels always in severity order
            return new Hour(operation.hourStart(), operation.total(), byLevel, operation.attended(),
                    operation.averageWaitMinutes(), operation.p95WaitMinutes());
        }
    }

    public static OperationReportResponse from(OperationReport report) {
        return new OperationReportResponse(
                report.range().from(),
                report.range().to(),
                report.hours().stream().map(Hour::from).toList());
    }
}