package com.vida_cruz.triage_service.infraestructure.configuration;

import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.model.SlaPolicy;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.usecase.RegisterPatientUseCase;
import com.vida_cruz.triage_service.domain.usecase.TriageClassifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}