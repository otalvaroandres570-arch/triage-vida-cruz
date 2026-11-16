package com.vida_cruz.triage_service.domain.model;

import java.util.List;

public record OperationReport(ReportRange range, List<HourlyOperation> hours) {
}