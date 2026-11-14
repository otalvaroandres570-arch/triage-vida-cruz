package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.exceptions.InvalidReportRangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportRangeTest {

    private static final Instant FROM = Instant.parse("2026-11-13T00:00:00Z");

    @Test
    void shouldAcceptTheLongestAllowedRange() {
        assertDoesNotThrow(() -> new ReportRange(FROM, FROM.plus(Duration.ofDays(31))));
    }

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void shouldRejectInvalidRanges(Instant from, Instant to) {
        assertThrows(InvalidReportRangeException.class, () -> new ReportRange(from, to));
    }

    static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of(null, FROM),
                Arguments.of(FROM, null),
                Arguments.of(FROM, FROM),
                Arguments.of(FROM, FROM.minusSeconds(1)),
                Arguments.of(FROM, FROM.plus(Duration.ofDays(31)).plusSeconds(1)));
    }
}