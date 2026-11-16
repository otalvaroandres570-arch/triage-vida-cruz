package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.HourlyOperation;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.OperationReportRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class R2dbcOperationReportRepository implements OperationReportRepository {

    private static final Map<TriageLevel, String> LEVEL_COLUMNS = Map.of(
            TriageLevel.CRITICO, "critical",
            TriageLevel.URGENTE, "urgent",
            TriageLevel.URGENTE_2, "semi_urgent",
            TriageLevel.NO_URGENTE, "non_urgent",
            TriageLevel.NO_URGENTE_2, "administrative");

    private final DatabaseClient databaseClient;

    @Override
    public Flux<HourlyOperation> findHourly(ReportRange range) {
        return databaseClient.sql(QueryConstants.HOURLY_OPERATION)
                .bind("rangeStart", range.from().atOffset(ZoneOffset.UTC))
                .bind("rangeEnd", range.to().atOffset(ZoneOffset.UTC))
                .map((row, metadata) -> toHourlyOperation(row))
                .all();
    }

    private static HourlyOperation toHourlyOperation(Row row) {
        Map<TriageLevel, Long> byLevel = Arrays.stream(TriageLevel.values())
                .collect(Collectors.toMap(Function.identity(), level -> row.get(LEVEL_COLUMNS.get(level), Long.class)));

        return new HourlyOperation(
                Objects.requireNonNull(row.get("hour_start", OffsetDateTime.class)).toInstant(),
                row.get("total", Long.class),
                byLevel,
                row.get("attended", Long.class),
                minutes(row, "avg_wait_minutes"),
                minutes(row, "p95_wait_minutes"));
    }

    private static Double minutes(Row row, String column) {
        return Optional.ofNullable(row.get(column, BigDecimal.class))
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }
}