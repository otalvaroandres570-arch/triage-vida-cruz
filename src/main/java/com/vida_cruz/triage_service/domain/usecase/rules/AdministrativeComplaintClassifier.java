
package com.vida_cruz.triage_service.domain.usecase.rules;

import com.vida_cruz.triage_service.domain.usecase.util.TextNormalizer;

import java.util.Set;

public class AdministrativeComplaintClassifier {

    private static final Set<String> ADMINISTRATIVE_COMPLAINTS = Set.of(
            "documentos",
            "recoleccion de resultados",
            "renovacion de recetario",
            "vacunacion",
            "seguimiento"
    );

    public boolean isAdministrative(String chiefComplaint) {

        if (chiefComplaint == null || chiefComplaint.isBlank()) {
            return false;
        }

        return ADMINISTRATIVE_COMPLAINTS.contains(
                TextNormalizer.normalize(chiefComplaint)
        );
    }
}
