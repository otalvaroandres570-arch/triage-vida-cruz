package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.*;

public record SlaPolicy(Map<TriageLevel, Integer> minutesByLevel) {

    public SlaPolicy {
        minutesByLevel = validated(minutesByLevel);
    }

    /** Every level must have a positive SLA; returns an immutable copy. */
    private static Map<TriageLevel, Integer> validated(Map<TriageLevel, Integer> minutesByLevel) {
        Objects.requireNonNull(minutesByLevel, "minutesByLevel must not be null");

        Arrays.stream(TriageLevel.values())
                .filter(level -> !hasPositiveMinutes(minutesByLevel, level))
                .findFirst()
                .ifPresent(level -> {
                    throw new IllegalArgumentException(
                            "SLA for level %s must be a positive number of minutes".formatted(level));
                });

        return Map.copyOf(minutesByLevel);
    }

    private static boolean hasPositiveMinutes(Map<TriageLevel, Integer> minutesByLevel, TriageLevel level) {
        return Optional.ofNullable(minutesByLevel.get(level))
                .filter(minutes -> minutes > 0)
                .isPresent();
    }

    public static SlaPolicy defaults() {
        return new SlaPolicy(Map.of(
                CRITICO, 5,
                URGENTE, 10,
                URGENTE_2, 30,
                NO_URGENTE, 60,
                NO_URGENTE_2, 120
        ));
    }

    public int minutesFor(TriageLevel level) {
        return minutesByLevel.get(level);
    }
}
