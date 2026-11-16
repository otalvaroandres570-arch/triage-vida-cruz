package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.exceptions.InvalidReportRangeException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Range of a report: {@code from} inclusive, {@code to} exclusive. */
public record ReportRange(Instant from, Instant to) {

    public static final Duration MAX_LENGTH = Duration.ofDays(31);

    public ReportRange {
        Instant start = require(from, Objects::nonNull, "El parámetro 'desde' es obligatorio");
        Instant end = require(to, Objects::nonNull, "El parámetro 'hasta' es obligatorio");
        Duration length = Duration.between(start, end);

        require(length, Duration::isPositive, "El parámetro 'desde' debe ser anterior a 'hasta'");
        require(length, value -> value.compareTo(MAX_LENGTH) <= 0,
                "El rango del reporte no puede superar %d días".formatted(MAX_LENGTH.toDays()));
    }

    private static <T> T require(T value, Predicate<T> condition, String message) {
        return Optional.ofNullable(value)
                .filter(condition)
                .orElseThrow(() -> new InvalidReportRangeException(message));
    }
}
