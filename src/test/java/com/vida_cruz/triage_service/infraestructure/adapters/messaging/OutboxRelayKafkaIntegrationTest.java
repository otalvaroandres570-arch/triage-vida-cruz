package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.domain.api.ChangePatientStatus;
import com.vida_cruz.triage_service.domain.api.PublishPendingEvents;
import com.vida_cruz.triage_service.domain.api.RegisterPatient;
import com.vida_cruz.triage_service.domain.exceptions.EventPublishingException;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.RegisterPatientCommand;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.EventOutbox;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import com.vida_cruz.triage_service.domain.spi.PendingEventStore;
import com.vida_cruz.triage_service.domain.usecase.PublishPendingEventsUseCase;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.kafka.KafkaContainer;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRelayKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    // 3.9.1, not 3.9.0: the 3.9.0 image rejects Testcontainers' default listeners on startup
    // ("advertised.listeners cannot use the nonroutable meta-address 0.0.0.0"). Fixed in 3.9.1.
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
    }

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-11T15:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(600);

    @Autowired
    private PendingEventStore store;

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private PatientRepository repository;

    @Autowired
    private RegisterPatient registerPatient;

    @Autowired
    private ChangePatientStatus changePatientStatus;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<KafkaSender<String, String>> senders = new ArrayList<>();

    @BeforeEach
    void cleanOutbox() {
        databaseClient.sql("DELETE FROM outbox_event").fetch().rowsUpdated().block();
    }

    @AfterEach
    void closeSenders() {
        senders.forEach(KafkaSender::close);
    }

    @Test
    void shouldPublishPendingEventsToKafkaInOrderAndMarkThemPublished() throws Exception {
        String topic = createTopic();
        seedLifecycleEvents();

        StepVerifier.create(relayTo(KAFKA.getBootstrapServers(), topic).publishBatch())
                .expectNext(3)
                .verifyComplete();

        List<ConsumerRecord<String, String>> records = consume(topic, 3, Duration.ofSeconds(20));
        assertEquals(List.of("paciente_registrado", "paciente_en_consulta", "paciente_alta"),
                records.stream().map(record -> json(record).get("type").asText()).toList());
        assertEquals(1, records.stream().map(ConsumerRecord::partition).distinct().count(),
                "all events of a patient go to the same partition");
        assertEquals(0, pendingCount());
        assertEquals(3, publishedCount());
    }

    @Test
    void shouldKeepEventsPendingWhileKafkaIsDownAndPublishThemWhenItIsBack() throws Exception {
        String topic = createTopic();
        seedLifecycleEvents();

        StepVerifier.create(relayTo("localhost:1", topic).publishBatch())
                .expectError(EventPublishingException.class)
                .verify(Duration.ofSeconds(30));

        assertEquals(3, pendingCount(), "nothing is lost while the broker is unreachable");
        assertEquals(0, publishedCount());

        StepVerifier.create(relayTo(KAFKA.getBootstrapServers(), topic).publishBatch())
                .expectNext(3)
                .verifyComplete();

        List<ConsumerRecord<String, String>> records = consume(topic, 3, Duration.ofSeconds(20));
        assertEquals(List.of("paciente_registrado", "paciente_en_consulta", "paciente_alta"),
                records.stream().map(record -> json(record).get("type").asText()).toList());
        assertEquals(0, pendingCount());
    }

    @Test
    void shouldNotRepublishEventsThatWerePublishedBefore() throws Exception {
        String topic = createTopic();
        seedLifecycleEvents();
        PublishPendingEvents relay = relayTo(KAFKA.getBootstrapServers(), topic);

        StepVerifier.create(relay.publishBatch()).expectNext(3).verifyComplete();
        StepVerifier.create(relay.publishBatch()).expectNext(0).verifyComplete();

        assertEquals(3, consume(topic, 4, Duration.ofSeconds(5)).size());
    }

    @Test
    void shouldPublishTheEventsGeneratedByTheUseCases() throws Exception {
        String topic = createTopic();
        Patient patient = registerPatient.register(new RegisterPatientCommand(
                "Juan Pérez", 40, new VitalSigns(130, 180, 110, 97, 36.8, 8, 15), "Dolor torácico")).block();
        Assertions.assertNotNull(patient);
        changePatientStatus.change(patient.id(), PatientStatus.EN_CONSULTA).block();

        StepVerifier.create(relayTo(KAFKA.getBootstrapServers(), topic).publishBatch())
                .expectNext(2)
                .verifyComplete();

        List<ConsumerRecord<String, String>> records = consume(topic, 2, Duration.ofSeconds(20));
        assertEquals(2, records.size());
        records.forEach(record -> assertEquals(patient.id().toString(), record.key()));

        JsonNode registered = json(records.get(0));
        assertEquals("paciente_registrado", registered.get("type").asText());
        assertEquals("CRITICO", registered.get("level").asText());
        assertEquals("REGISTRADO", registered.get("status").asText());
        assertTrue(registered.get("previousStatus").isNull());
        assertEquals(5, registered.get("slaMinutes").asInt());

        JsonNode inConsultation = json(records.get(1));
        assertEquals("paciente_en_consulta", inConsultation.get("type").asText());
        assertEquals("EN_CONSULTA", inConsultation.get("status").asText());
        assertEquals("REGISTRADO", inConsultation.get("previousStatus").asText());
    }

    // ---------- helpers ----------

    private PublishPendingEvents relayTo(String bootstrapServers, String topic) {
        KafkaSender<String, String> sender = KafkaSenderFactory.create(bootstrapServers);
        senders.add(sender);
        return new PublishPendingEventsUseCase(store,
                new KafkaEventPublisher(sender, objectMapper, topic), Clock.systemUTC(), 100);
    }

    private void seedLifecycleEvents() {
        Patient registered = savePatient();
        Patient inConsultation = registered.transitionTo(PatientStatus.EN_CONSULTA, LATER);
        Patient discharged = inConsultation.transitionTo(PatientStatus.ALTA, LATER.plusSeconds(60));

        Flux.just(
                        TriageEvent.registration(registered),
                        TriageEvent.statusChange(registered, inConsultation),
                        TriageEvent.statusChange(inConsultation, discharged))
                .concatMap(outbox::append)
                .then()
                .block();
    }

    private Patient savePatient() {
        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Juan Pérez")
                .age(40)
                .vitals(new VitalSigns(130, 180, 110, 97, 38.2, 8, 15))
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICO)
                .slaMinutes(5)
                .status(PatientStatus.REGISTRADO)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
        return repository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private long pendingCount() {
        return count("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL");
    }

    private long publishedCount() {
        return count("SELECT COUNT(*) FROM outbox_event WHERE published_at IS NOT NULL");
    }

    private long count(String sql) {
        return databaseClient.sql(sql).map(row -> row.get(0, Long.class)).one().block();
    }

    private JsonNode json(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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
}