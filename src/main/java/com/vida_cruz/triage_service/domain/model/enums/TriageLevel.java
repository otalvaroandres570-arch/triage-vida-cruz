package com.vida_cruz.triage_service.domain.model.enums;

import java.util.Arrays;

public enum TriageLevel {
    CRITICO(1),
    URGENTE(2),
    URGENTE_2(3),
    NO_URGENTE(4),
    NO_URGENTE_2(5);

    private final int value;

    TriageLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TriageLevel fromValue(int value) {
        return Arrays.stream(values())
                .filter(level -> level.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown triage level value: " + value));
    }
}
