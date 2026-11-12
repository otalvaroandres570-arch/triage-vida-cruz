package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vida_cruz.triage_service.domain.model.TriageEvent;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageEventType;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageEventMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-12T17:55:19.473818Z");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @ParameterizedTest(name = "{0} is published as {1}")
    @CsvSource({
            "PATIENT_REGISTERED,paciente_registrado",
            "PATIENT_ENTERED_CONSULTATION,paciente_en_consulta",
            "PATIENT_DISCHARGED,paciente_alta",
            "PATIENT_ESCALATED,paciente_escalado"
    })
    void shouldUseTheEventNamesOfTheSpecification(TriageEventType type, String expectedName) {
        TriageEventMessage message = TriageEventMessage.from(event(type, PatientStatus.REGISTRADO));

        assertEquals(expectedName, message.type());
    }

    @Test
    void shouldContainExactlyTheFieldsOfTheContractAndNoPersonalData() {
        JsonNode json = MAPPER.valueToTree(
                TriageEventMessage.from(event(TriageEventType.PATIENT_REGISTERED, null)));

        Set<String> fields = new TreeSet<>();
        json.fieldNames().forEachRemaining(fields::add);

        assertEquals(new TreeSet<>(Set.of("eventId", "type", "version", "occurredAt", "patientId",
                "level", "status", "previousStatus", "slaMinutes")), fields);
    }

    @Test
    void shouldSerializeValuesAsDocumentedInTheContract() {
        TriageEvent event = event(TriageEventType.PATIENT_ENTERED_CONSULTATION, PatientStatus.REGISTRADO);

        JsonNode json = MAPPER.valueToTree(TriageEventMessage.from(event));

        assertEquals(event.eventId().toString(), json.get("eventId").asText());
        assertEquals(1, json.get("version").asInt());
        assertEquals("2026-11-12T17:55:19.473818Z", json.get("occurredAt").asText());
        assertEquals(event.patientId().toString(), json.get("patientId").asText());
        assertEquals("CRITICO", json.get("level").asText());
        assertEquals("EN_CONSULTA", json.get("status").asText());
        assertEquals("REGISTRADO", json.get("previousStatus").asText());
        assertEquals(5, json.get("slaMinutes").asInt());
    }

    @Test
    void shouldSerializePreviousStatusAsExplicitNullForARegistration() {
        JsonNode json = MAPPER.valueToTree(
                TriageEventMessage.from(event(TriageEventType.PATIENT_REGISTERED, null)));

        assertTrue(json.has("previousStatus"));
        assertTrue(json.get("previousStatus").isNull());
    }

    private static TriageEvent event(TriageEventType type, PatientStatus previousStatus) {
        PatientStatus status = type == TriageEventType.PATIENT_ENTERED_CONSULTATION
                ? PatientStatus.EN_CONSULTA : PatientStatus.REGISTRADO;
        return new TriageEvent(UUID.randomUUID(), type, NOW, UUID.randomUUID(),
                TriageLevel.CRITICO, status, previousStatus, 5);
    }
}