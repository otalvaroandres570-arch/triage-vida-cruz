package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaPropertiesTest {

    @Test
    void shouldFallBackToBusinessRuleDefaultsWhenNothingIsConfigured() {
        SlaProperties properties = new SlaProperties(null, null, null, null, null);

        assertEquals(SlaPolicy.defaults(), properties.toPolicy());
    }

    @Test
    void shouldOverrideOnlyTheConfiguredLevels() {
        SlaProperties properties = new SlaProperties(1, null, null, null, 7);

        SlaPolicy policy = properties.toPolicy();

        assertEquals(1, policy.minutesFor(TriageLevel.CRITICO));
        assertEquals(10, policy.minutesFor(TriageLevel.URGENTE));
        assertEquals(30, policy.minutesFor(TriageLevel.URGENTE_2));
        assertEquals(60, policy.minutesFor(TriageLevel.NO_URGENTE));
        assertEquals(7, policy.minutesFor(TriageLevel.NO_URGENTE_2));
    }

    @Test
    void shouldRejectNonPositiveConfiguredValues() {
        SlaProperties properties = new SlaProperties(0, null, null, null, null);

        assertThrows(IllegalArgumentException.class, properties::toPolicy);
    }
}