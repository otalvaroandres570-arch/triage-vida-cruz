package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

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
        Map<TriageLevel, Integer> minutes = new EnumMap<>(TriageLevel.class);

        minutes.put(TriageLevel.CRITICO, orDefault(critico, defaults, TriageLevel.CRITICO));
        minutes.put(TriageLevel.URGENTE, orDefault(urgente, defaults, TriageLevel.URGENTE));
        minutes.put(TriageLevel.URGENTE_2, orDefault(urgente2, defaults, TriageLevel.URGENTE_2));
        minutes.put(TriageLevel.NO_URGENTE, orDefault(noUrgente, defaults, TriageLevel.NO_URGENTE));
        minutes.put(TriageLevel.NO_URGENTE_2, orDefault(noUrgente2, defaults, TriageLevel.NO_URGENTE_2));

        return new SlaPolicy(minutes);
    }

    private static int orDefault(Integer configured, SlaPolicy defaults, TriageLevel level) {
        return configured != null ? configured : defaults.minutesFor(level);
    }
}