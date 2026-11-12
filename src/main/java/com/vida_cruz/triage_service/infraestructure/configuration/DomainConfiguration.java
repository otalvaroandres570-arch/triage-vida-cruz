package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.usecase.RegisterPatientUseCase;
import com.vida_cruz.triage_service.domain.usecase.TriageClassifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.vida_cruz.triage_service.domain.api.ChangePatientStatus;
import com.vida_cruz.triage_service.domain.api.GetPatient;
import com.vida_cruz.triage_service.domain.usecase.ChangePatientStatusUseCase;
import com.vida_cruz.triage_service.domain.usecase.GetPatientUseCase;

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
                                           Clock clock) {
        return new RegisterPatientUseCase(classifier, slaPolicy, repository, clock);
    }

    @Bean
    public ChangePatientStatus changePatientStatus(PatientRepository repository, Clock clock) {
        return new ChangePatientStatusUseCase(repository, clock);
    }

    @Bean
    public GetPatient getPatient(PatientRepository repository, Clock clock) {
        return new GetPatientUseCase(repository, clock);
    }
}