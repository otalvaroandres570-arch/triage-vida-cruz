package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vida_cruz.triage_service.domain.exceptions.EventPublishingException;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class KafkaEventPublisherIntegrationTest {

    // 3.9.1, not 3.9.0: the 3.9.0 image rejects Testcontainers' default listeners on startup
    // ("advertised.listeners cannot use the nonroutable meta-address 0.0.0.0"). Fixed in 3.9.1.
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    private static final Instant NOW = Instant.parse("2026-11-12T17:55:19.473818Z");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final List<KafkaSender<String, String>> senders = new ArrayList<>();

    @AfterEach
    void closeSenders() {
        senders.forEach(KafkaSender::close);
    }

    @Test
    void shouldPublishJsonEventKeyedByPatientId() throws Exception {
        String topic = createTopic();
        UUID patientId = UUID.randomUUID();
        TriageEvent event = event(TriageEventType.PATIENT_REGISTERED, null, PatientStatus.REGISTRADO, patientId);

        StepVerifier.create(publisherFor(topic).publish(event))
                .verifyComplete();

        List<ConsumerRecord<String, String>> records = consume(topic, 1, Duration.ofSeconds(20));
        assertEquals(1, records.size());
        ConsumerRecord<String, String> record = records.get(0);
        assertEquals(patientId.toString(), record.key());

        JsonNode json = MAPPER.readTree(record.value());
        assertEquals(event.eventId().toString(), json.get("eventId").asText());
        assertEquals("paciente_registrado", json.get("type").asText());
        assertEquals(1, json.get("version").asInt());
        assertEquals(NOW, Instant.parse(json.get("occurredAt").asText()));
        assertEquals(patientId.toString(), json.get("patientId").asText());
        assertEquals("CRITICO", json.get("level").asText());
        assertEquals("REGISTRADO", json.get("status").asText());
        assertTrue(json.get("previousStatus").isNull());
        assertEquals(5, json.get("slaMinutes").asInt());
    }


    @Test
    void shouldNotSendAnythingUntilSubscribed() throws Exception {
        String topic = createTopic();
        KafkaEventPublisher publisher = publisherFor(topic);

        publisher.publish(event(TriageEventType.PATIENT_REGISTERED, null, PatientStatus.REGISTRADO,
                UUID.randomUUID()));

        assertTrue(consume(topic, 1, Duration.ofSeconds(3)).isEmpty());
    }

    @Test
    void shouldFailWithEventPublishingExceptionWhenBrokerIsUnreachable() {
        KafkaEventPublisher publisher =
                new KafkaEventPublisher(newSender("localhost:1"), MAPPER, "triaje.eventos");
        TriageEvent event = event(TriageEventType.PATIENT_REGISTERED, null, PatientStatus.REGISTRADO,
                UUID.randomUUID());

        StepVerifier.create(publisher.publish(event))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(EventPublishingException.class, error);
                    assertNotNull(error.getCause());
                    assertTrue(error.getMessage().contains(event.patientId().toString()));
                })
                .verify(Duration.ofSeconds(30));
    }

    // ---------- helpers ----------

    private KafkaEventPublisher publisherFor(String topic) {
        return new KafkaEventPublisher(newSender(KAFKA.getBootstrapServers()), MAPPER, topic);
    }

    private KafkaSender<String, String> newSender(String bootstrapServers) {
        KafkaSender<String, String> sender = KafkaSenderFactory.create(bootstrapServers);
        senders.add(sender);
        return sender;
    }

    private String createTopic() throws Exception {
        String topic = "triaje.eventos.test-" + UUID.randomUUID();
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(config)) {
            admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1))).all().get();
        }
        return topic;
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected, Duration timeout) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (records.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    private static TriageEvent event(TriageEventType type, PatientStatus previousStatus,
                                     PatientStatus status, UUID patientId) {
        return new TriageEvent(UUID.randomUUID(), type, NOW, patientId,
                TriageLevel.CRITICO, status, previousStatus, 5);
    }
}