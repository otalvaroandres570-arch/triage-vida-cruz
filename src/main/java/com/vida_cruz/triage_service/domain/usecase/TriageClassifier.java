
package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.usecase.rules.AdministrativeComplaintClassifier;
import com.vida_cruz.triage_service.domain.usecase.rules.VitalSignsTriageRules;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public class TriageClassifier {

    private final VitalSignsTriageRules vitalSignsRules;
    private final AdministrativeComplaintClassifier complaintClassifier;

    public TriageClassifier() {
        this.vitalSignsRules = new VitalSignsTriageRules();
        this.complaintClassifier = new AdministrativeComplaintClassifier();
    }

    public TriageLevel classify(VitalSigns vitals, String chiefComplaint) {

        Objects.requireNonNull(vitals, "vitals must not be null");

        if (vitalSignsRules.isCritical(vitals)) {
            return TriageLevel.CRITICO;
        }

        if (vitalSignsRules.isUrgent(vitals)) {
            return TriageLevel.URGENTE;
        }

        if (vitalSignsRules.isSemiUrgent(vitals)) {
            return TriageLevel.URGENTE_2;
        }

        return isAdministrative(chiefComplaint);
    }

    @Nonnull
    private TriageLevel isAdministrative(String chiefComplaint) {
        return complaintClassifier.isAdministrative(chiefComplaint)
                ? TriageLevel.NO_URGENTE_2
                : TriageLevel.NO_URGENTE;
    }
}
