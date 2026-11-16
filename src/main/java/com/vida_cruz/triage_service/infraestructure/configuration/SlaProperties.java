package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Optional overrides of the SLA (in minutes) per triage level.
 * Any level left unset keeps the business-rule default (RN-02).
 */
@ConfigurationProperties(prefix = "triage.sla")
public record SlaProperties(
        Integer critico,
        Integer urgente,
        Integer urgente2,
        Integer noUrgente,
        Integer noUrgente2
) {

    public SlaPolicy toPolicy() {
        SlaPolicy defaults = SlaPolicy.defaults();
        return new SlaPolicy(Arrays.stream(TriageLevel.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        level -> configured(level).orElseGet(() -> defaults.minutesFor(level)))));
    }

    private Optional<Integer> configured(TriageLevel level) {
        return Optional.ofNullable(switch (level) {
            case CRITICO -> critico;
            case URGENTE -> urgente;
            case URGENTE_2 -> urgente2;
            case NO_URGENTE -> noUrgente;
            case NO_URGENTE_2 -> noUrgente2;
        });
    }
}