package com.vida_cruz.triage_service.domain.api;

import com.vida_cruz.triage_service.domain.model.OperationReport;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import reactor.core.publisher.Mono;

public interface GenerateOperationReport {

    Mono<OperationReport> generate(ReportRange range);
}