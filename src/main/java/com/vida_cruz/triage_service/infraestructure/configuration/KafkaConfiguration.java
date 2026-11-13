package com.vida_cruz.triage_service.infraestructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import com.vida_cruz.triage_service.infraestructure.adapters.messaging.KafkaEventPublisher;
import com.vida_cruz.triage_service.infraestructure.adapters.messaging.KafkaSenderFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;

@Configuration
@EnableConfigurationProperties(TriageKafkaProperties.class)
public class KafkaConfiguration {

    @Bean
    public KafkaSender<String, String> kafkaSender(TriageKafkaProperties properties) {
        return KafkaSenderFactory.create(properties.bootstrapServers());
    }

    @Bean
    public EventPublisher eventPublisher(KafkaSender<String, String> sender,
                                         ObjectMapper objectMapper,
                                         TriageKafkaProperties properties) {
        return new KafkaEventPublisher(sender, objectMapper, properties.topic());
    }
}