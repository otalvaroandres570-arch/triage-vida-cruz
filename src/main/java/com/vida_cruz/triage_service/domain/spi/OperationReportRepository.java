package com.vida_cruz.triage_service.domain.spi;

import com.vida_cruz.triage_service.domain.model.HourlyOperation;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import reactor.core.publisher.Flux;

public interface OperationReportRepository {

    /** One element per hour with registrations, oldest first. The aggregation is done by the data source. */
    Flux<HourlyOperation> findHourly(ReportRange range);
}