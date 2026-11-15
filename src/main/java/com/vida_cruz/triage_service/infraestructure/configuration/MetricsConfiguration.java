package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.spi.TriageMetrics;
import com.vida_cruz.triage_service.infraestructure.adapters.metrics.MicrometerTriageMetrics;
import com.vida_cruz.triage_service.infraestructure.adapters.metrics.PendingWorkGauges;
import com.vida_cruz.triage_service.infraestructure.entrypoints.scheduler.PeriodicJob;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;

@Configuration
public class MetricsConfiguration {

    @Bean
    public TriageMetrics triageMetrics(MeterRegistry registry) {
        return new MicrometerTriageMetrics(registry);
    }

    @Bean
    public PendingWorkGauges pendingWorkGauges(DatabaseClient databaseClient, MeterRegistry registry) {
        return new PendingWorkGauges(databaseClient, registry);
    }

    @Bean
    public PeriodicJob pendingWorkGaugesJob(PendingWorkGauges gauges) {
        return new PeriodicJob("Pending work gauges", Duration.ofSeconds(15), gauges::refresh);
    }
}