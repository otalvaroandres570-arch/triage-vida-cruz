package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.domain.exceptions.EventPublishingException;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.spi.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;
    private final String topic;

    @Override
    public Mono<Void> publish(TriageEvent event) {
        return Mono.fromCallable(() -> toRecord(event))
                .flatMap(record -> sender.send(Mono.just(record)).then())
                .doOnSuccess(ignored -> log.debug("Published {} for patient {}", event.type(), event.patientId()))
                .onErrorMap(error -> new EventPublishingException(event, error));
    }

    private SenderRecord<String, String, UUID> toRecord(TriageEvent event) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(TriageEventMessage.from(event));
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.patientId().toString(), payload);
        return SenderRecord.create(record, event.eventId());
    }
}