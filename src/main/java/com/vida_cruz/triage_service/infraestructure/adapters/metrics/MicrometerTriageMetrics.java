package com.vida_cruz.triage_service.infraestructure.adapters.metrics;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.TriageMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MicrometerTriageMetrics implements TriageMetrics {

    private final Map<TriageLevel, Counter> classifications;
    private final Map<TriageLevel, Counter> escalations;

    public MicrometerTriageMetrics(MeterRegistry registry) {
        this.classifications = countersByLevel(registry, "triage.classifications",
                "Patients classified at registration, by triage level");
        this.escalations = countersByLevel(registry, "triage.escalations",
                "Patients escalated for exceeding their SLA, by triage level");
    }

    @Override
    public void patientClassified(TriageLevel level) {
        classifications.get(level).increment();
    }

    @Override
    public void patientEscalated(TriageLevel level) {
        escalations.get(level).increment();
    }

    /** All five levels are registered up front, so every series exists (at zero) from the start. */
    private static Map<TriageLevel, Counter> countersByLevel(MeterRegistry registry, String name, String description) {
        return Arrays.stream(TriageLevel.values())
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        level -> Counter.builder(name)
                                .description(description)
                                .tag("level", level.name())
                                .register(registry)));
    }
}