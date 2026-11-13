package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.usecase.rules.AdministrativeComplaintClassifier;
import com.vida_cruz.triage_service.domain.usecase.rules.VitalSignsTriageRules;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class TriageClassifier {

    /** A vital-signs rule: if it matches, the patient gets that level. */
    private record Rule(Predicate<VitalSigns> matches, TriageLevel level) {
    }

    private final List<Rule> vitalSignsRules;
    private final AdministrativeComplaintClassifier complaintClassifier;

    public TriageClassifier() {
        VitalSignsTriageRules rules = new VitalSignsTriageRules();
        // Evaluated in order of severity: the first rule that matches wins.
        this.vitalSignsRules = List.of(
                new Rule(rules::isCritical, TriageLevel.CRITICO),
                new Rule(rules::isUrgent, TriageLevel.URGENTE),
                new Rule(rules::isSemiUrgent, TriageLevel.URGENTE_2));
        this.complaintClassifier = new AdministrativeComplaintClassifier();
    }

    public TriageLevel classify(VitalSigns vitals, String chiefComplaint) {
        Objects.requireNonNull(vitals, "vitals must not be null");

        return vitalSignsRules.stream()
                .filter(rule -> rule.matches().test(vitals))
                .map(Rule::level)
                .findFirst()
                .orElseGet(() -> classifyByComplaint(chiefComplaint));
    }

    private TriageLevel classifyByComplaint(String chiefComplaint) {
        return complaintClassifier.isAdministrative(chiefComplaint)
                ? TriageLevel.NO_URGENTE_2
                : TriageLevel.NO_URGENTE;
    }
}
