package com.vida_cruz.triage_service.domain.model;

import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;

import java.time.Instant;
import java.util.Map;

/** Operation of one UTC hour, grouped by registration time. Wait statistics are null if nobody was attended. */
public record HourlyOperation(
        Instant hourStart,
        long total,
        Map<TriageLevel, Long> byLevel,
        long attended,
        Double averageWaitMinutes,
        Double p95WaitMinutes
) {
}