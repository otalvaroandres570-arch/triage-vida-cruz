
package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.*;

public record SlaPolicy(Map<TriageLevel, Integer> minutesByLevel) {

    public SlaPolicy {
        Objects.requireNonNull(minutesByLevel, "minutesByLevel must not be null");

        Map<TriageLevel, Integer> finalMinutesByLevel = minutesByLevel;

        Arrays.stream(TriageLevel.values())
                .filter(level -> {
                    return validate(level, finalMinutesByLevel);
                })
                .findFirst()
                .ifPresent(SlaPolicy::throwException);

        minutesByLevel = Map.copyOf(minutesByLevel);
    }

    private static boolean validate(TriageLevel level, Map<TriageLevel, Integer> finalMinutesByLevel) {
        Integer minutes = finalMinutesByLevel.get(level);
        return minutes == null || minutes <= 0;
    }

    private static void throwException(TriageLevel level) {
        throw new IllegalArgumentException(
                "SLA for level %s must be a positive number of minutes".formatted(level));
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
