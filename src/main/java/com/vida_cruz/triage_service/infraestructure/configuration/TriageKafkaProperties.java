package com.vida_cruz.triage_service.infraestructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "triage.kafka")
public record TriageKafkaProperties(
        @DefaultValue("localhost:9092") String bootstrapServers,
        @DefaultValue("triaje.eventos") String topic
) {
}