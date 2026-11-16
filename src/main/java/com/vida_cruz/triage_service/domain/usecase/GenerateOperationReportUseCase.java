package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.api.GenerateOperationReport;
import com.vida_cruz.triage_service.domain.model.OperationReport;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import com.vida_cruz.triage_service.domain.spi.OperationReportRepository;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class GenerateOperationReportUseCase implements GenerateOperationReport {

    private final OperationReportRepository repository;

    public GenerateOperationReportUseCase(OperationReportRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Mono<OperationReport> generate(ReportRange range) {
        return repository.findHourly(range)
                .collectList()
                .map(hours -> new OperationReport(range, hours));
    }
}