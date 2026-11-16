package com.vida_cruz.triage_service.domain.usecase.rules;

import com.vida_cruz.triage_service.domain.usecase.util.TextNormalizer;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class AdministrativeComplaintClassifier {

    private static final Set<String> ADMINISTRATIVE_COMPLAINTS = Set.of(
            "documentos",
            "recoleccion de resultados",
            "renovacion de recetario",
            "vacunacion",
            "seguimiento"
    );

    public boolean isAdministrative(String chiefComplaint) {
        return Optional.ofNullable(chiefComplaint)
                .filter(Predicate.not(String::isBlank))
                .map(TextNormalizer::normalize)
                .filter(ADMINISTRATIVE_COMPLAINTS::contains)
                .isPresent();
    }
}
