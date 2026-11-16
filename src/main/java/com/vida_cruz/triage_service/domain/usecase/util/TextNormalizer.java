package com.vida_cruz.triage_service.domain.usecase.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public final class TextNormalizer {

    private TextNormalizer() {
        // Utility class
    }

    public static String normalize(String text) {
        return Optional.ofNullable(text)
                .map(value -> Normalizer.normalize(value, Normalizer.Form.NFD))
                .map(value -> value.replaceAll("\\p{M}", ""))
                .map(String::trim)
                .map(value -> value.replaceAll("\\s+", " "))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
    }
}
