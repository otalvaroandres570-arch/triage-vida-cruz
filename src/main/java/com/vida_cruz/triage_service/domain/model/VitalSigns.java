package com.vida_cruz.triage_service.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record VitalSigns(
        int heartRate,
        int systolicBp,
        int diastolicBp,
        int spo2,
        double temperature,
        int pain,
        int gcs
) {
}
