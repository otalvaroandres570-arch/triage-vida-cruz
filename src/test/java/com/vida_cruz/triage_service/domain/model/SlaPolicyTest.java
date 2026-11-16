package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} minutes")
    @CsvSource({
            "CRITICO,5",
            "URGENTE,10",
            "URGENTE_2,30",
            "NO_URGENTE,60",
            "NO_URGENTE_2,120"
    })
    void shouldProvideBusinessRuleDefaults(TriageLevel level, int expectedMinutes) {
        assertEquals(expectedMinutes, SlaPolicy.defaults().minutesFor(level));
    }

    @Test
    void shouldAcceptCustomValues() {
        SlaPolicy policy = new SlaPolicy(allLevelsWith(1));

        for (TriageLevel level : TriageLevel.values()) {
            assertEquals(1, policy.minutesFor(level));
        }
    }

    @Test
    void shouldRejectMissingLevel() {
        Map<TriageLevel, Integer> incomplete = allLevelsWith(10);
        incomplete.remove(TriageLevel.NO_URGENTE_2);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> new SlaPolicy(incomplete));

        assertEquals("SLA for level NO_URGENTE_2 must be a positive number of minutes", error.getMessage());
    }

    @ParameterizedTest(name = "{0} minutes is rejected")
    @ValueSource(ints = {0, -5})
    void shouldRejectNonPositiveMinutes(int invalidMinutes) {
        Map<TriageLevel, Integer> config = allLevelsWith(10);
        config.put(TriageLevel.CRITICO, invalidMinutes);

        assertThrows(IllegalArgumentException.class, () -> new SlaPolicy(config));
    }

    private static Map<TriageLevel, Integer> allLevelsWith(int minutes) {
        Map<TriageLevel, Integer> map = new EnumMap<>(TriageLevel.class);
        for (TriageLevel level : TriageLevel.values()) {
            map.put(level, minutes);
        }
        return map;
    }
}