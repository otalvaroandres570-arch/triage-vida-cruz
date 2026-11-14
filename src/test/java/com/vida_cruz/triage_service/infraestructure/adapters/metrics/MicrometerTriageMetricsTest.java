package com.vida_cruz.triage_service.infraestructure.adapters.metrics;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerTriageMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerTriageMetrics metrics = new MicrometerTriageMetrics(registry);

    @Test
    void shouldExposeEveryLevelFromTheStartAndCountEachOneSeparately() {
        assertEquals(0.0, count("triage.classifications", TriageLevel.NO_URGENTE_2));

        metrics.patientClassified(TriageLevel.CRITICO);
        metrics.patientClassified(TriageLevel.CRITICO);
        metrics.patientEscalated(TriageLevel.URGENTE);

        assertEquals(2.0, count("triage.classifications", TriageLevel.CRITICO));
        assertEquals(0.0, count("triage.classifications", TriageLevel.URGENTE));
        assertEquals(1.0, count("triage.escalations", TriageLevel.URGENTE));
        assertEquals(0.0, count("triage.escalations", TriageLevel.CRITICO));
    }

    private double count(String name, TriageLevel level) {
        return registry.get(name).tag("level", level.name()).counter().count();
    }
}