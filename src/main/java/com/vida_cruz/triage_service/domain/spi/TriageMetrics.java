package com.vida_cruz.triage_service.domain.spi;


import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

public interface TriageMetrics {

    /** A patient was registered and classified. Called once the registration is committed. */
    void patientClassified(TriageLevel level);

    /** A patient was escalated for exceeding the SLA. Called once the escalation is committed. */
    void patientEscalated(TriageLevel level);
}