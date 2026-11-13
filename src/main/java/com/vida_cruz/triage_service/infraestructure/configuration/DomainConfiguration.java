package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.GenerateOperationReport;
import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.OperationReportRepository;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import com.vida_cruz.triage_service.domain.usecase.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.vida_cruz.triage_service.domain.api.ChangePatientStatus;
import com.vida_cruz.triage_service.domain.api.GetPatient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SlaProperties.class)
public class DomainConfiguration {

    @Bean
    public TriageClassifier triageClassifier() {
        return new TriageClassifier();
    }

    @Bean
    public SlaPolicy slaPolicy(SlaProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RegisterPatient registerPatient(TriageClassifier classifier,
                                           SlaPolicy slaPolicy,
                                           PatientRepository repository,
                                           EventOutbox outbox,
                                           TransactionRunner transaction,
                                           Clock clock) {
        return new RegisterPatientUseCase(classifier, slaPolicy, repository, outbox, transaction, clock);
    }

    @Bean
    public ChangePatientStatus changePatientStatus(PatientRepository repository,
                                                   EventOutbox outbox,
                                                   TransactionRunner transaction,
                                                   Clock clock) {
        return new ChangePatientStatusUseCase(repository, outbox, transaction, clock);
    }

    @Bean
    public GetPatient getPatient(PatientRepository repository, Clock clock) {
        return new GetPatientUseCase(repository, clock);
    }

    @Bean
    public GenerateOperationReport generateOperationReport(OperationReportRepository repository) {
        return new GenerateOperationReportUseCase(repository);
    }
}