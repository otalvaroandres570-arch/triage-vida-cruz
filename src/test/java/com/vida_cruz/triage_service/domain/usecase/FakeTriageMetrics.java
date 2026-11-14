package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.TriageMetrics;

import java.util.ArrayList;
import java.util.List;

class FakeTriageMetrics implements TriageMetrics {

    final List<TriageLevel> classified = new ArrayList<>();
    final List<TriageLevel> escalated = new ArrayList<>();

    @Override
    public void patientClassified(TriageLevel level) {
        classified.add(level);
    }

    @Override
    public void patientEscalated(TriageLevel level) {
        escalated.add(level);
    }
}