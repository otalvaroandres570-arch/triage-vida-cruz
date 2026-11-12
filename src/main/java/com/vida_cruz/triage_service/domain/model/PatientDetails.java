package com.vida_cruz.triage_service.domain.model;

import java.time.Duration;

public record PatientDetails(Patient patient, Duration timeInCurrentStatus) {
}