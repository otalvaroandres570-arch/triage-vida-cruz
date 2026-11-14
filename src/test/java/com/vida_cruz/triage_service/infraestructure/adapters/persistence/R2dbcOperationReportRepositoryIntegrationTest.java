package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.AbstractPostgresIntegrationTest;
import com.vida_cruz.triage_service.SyntheticPastData;
import com.vida_cruz.triage_service.domain.model.HourlyOperation;
import com.vida_cruz.triage_service.domain.model.Patient;
import com.vida_cruz.triage_service.domain.model.PatientAudit;
import com.vida_cruz.triage_service.domain.model.ReportRange;
import com.vida_cruz.triage_service.domain.model.VitalSigns;
import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import com.vida_cruz.triage_service.domain.model.enums.TriageLevel;
import com.vida_cruz.triage_service.domain.spi.OperationReportRepository;
import com.vida_cruz.triage_service.domain.spi.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.CRITICO;
import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.NO_URGENTE;
import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.NO_URGENTE_2;
import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.URGENTE;
import static com.vida_cruz.triage_service.domain.model.enums.TriageLevel.URGENTE_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcOperationReportRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant BASE = Instant.parse("1998-01-01T10:00:00Z");

    @Autowired
    private OperationReportRepository reportRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DatabaseClient databaseClient;

    /**
     * Hour 10: five patients (3 critical, 1 urgent, 1 non-urgent). Three were attended, waiting 10, 20 and 35 minutes
     * (the last one registered at 10:55 and attended at 11:30); one is still waiting; one was discharged directly.
     * Hour 11: one urgent patient attended after 2 minutes. Hour 13: one patient still waiting.
     */
    @BeforeEach
    void seedScenario() {
        SyntheticPastData.clean(databaseClient);
        attended(CRITICO, 5, 15);
        attended(CRITICO, 20, 40);
        waiting(URGENTE, 30);
        discharged();
        attended(CRITICO, 55, 90);
        attended(URGENTE, 70, 72);
        waiting(NO_URGENTE_2, 190);
    }

    @Test
    void shouldReportEachHourWithTotalsLevelsAndWaitStatistics() {
        List<HourlyOperation> hours = report(minutes(0), minutes(240));

        assertEquals(List.of(minutes(0), minutes(60), minutes(180)),
                hours.stream().map(HourlyOperation::hourStart).toList(), "hour 12 has no patients and is not listed");

        HourlyOperation ten = hours.getFirst();
        assertEquals(5, ten.total());
        assertEquals(Map.of(CRITICO, 3L, URGENTE, 1L, URGENTE_2, 0L, NO_URGENTE, 1L, NO_URGENTE_2, 0L), ten.byLevel());
        assertEquals(3, ten.attended());
        assertEquals(21.67, ten.averageWaitMinutes(), 0.001);
        assertEquals(33.5, ten.p95WaitMinutes(), 0.001);

        HourlyOperation eleven = hours.get(1);
        assertEquals(1, eleven.total());
        assertEquals(Map.of(CRITICO, 0L, URGENTE, 1L, URGENTE_2, 0L, NO_URGENTE, 0L, NO_URGENTE_2, 0L), eleven.byLevel());
        assertEquals(1, eleven.attended());
        assertEquals(2.0, eleven.averageWaitMinutes(), 0.001);
        assertEquals(2.0, eleven.p95WaitMinutes(), 0.001);

        HourlyOperation thirteen = hours.get(2);
        assertEquals(1, thirteen.total());
        assertEquals(0, thirteen.attended());
        assertNull(thirteen.averageWaitMinutes(), "nobody attended: no wait statistics, not zero");
        assertNull(thirteen.p95WaitMinutes());
    }

    @Test
    void shouldIncludeTheStartOfTheRangeAndExcludeItsEnd() {
        List<HourlyOperation> hours = report(minutes(5), minutes(30)); // 10:05 included, 10:30 excluded

        assertEquals(1, hours.size());
        assertEquals(2, hours.getFirst().total());
        assertEquals(2, hours.getFirst().attended());
        assertEquals(15.0, hours.getFirst().averageWaitMinutes(), 0.001);
        assertEquals(19.5, hours.getFirst().p95WaitMinutes(), 0.001);
    }

    @Test
    void shouldReturnNothingWhenThereAreNoPatientsInTheRange() {
        assertTrue(report(Instant.parse("2010-01-01T00:00:00Z"), Instant.parse("2010-01-02T00:00:00Z")).isEmpty());
    }

    // ---------- helpers ----------

    private List<HourlyOperation> report(Instant from, Instant to) {
        return reportRepository.findHourly(new ReportRange(from, to)).collectList().block();
    }

    private static Instant minutes(long offset) {
        return BASE.plus(Duration.ofMinutes(offset));
    }

    private void attended(TriageLevel level, long registeredAt, long enteredConsultationAt) {
        moveTo(register(level, registeredAt), PatientStatus.EN_CONSULTA, enteredConsultationAt);
    }

    private void waiting(TriageLevel level, long registeredAt) {
        register(level, registeredAt);
    }

    private void discharged() {
        moveTo(register(TriageLevel.NO_URGENTE, 50), PatientStatus.ALTA, 60);
    }

    private Patient register(TriageLevel level, long registeredAtMinutes) {
        Instant registeredAt = minutes(registeredAtMinutes);
        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente Prueba")
                .age(40)
                .vitals(new VitalSigns(72, 120, 80, 99, 36.5, 2, 15))
                .chiefComplaint("Prueba")
                .triageLevel(level)
                .slaMinutes(60)
                .status(PatientStatus.REGISTRADO)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
        return patientRepository.saveWithAudit(patient, PatientAudit.registration(patient)).block();
    }

    private void moveTo(Patient patient, PatientStatus target, long atMinutes) {
        Patient updated = patient.transitionTo(target, minutes(atMinutes));
        patientRepository.updateStatusWithAudit(updated, patient.status(), PatientAudit.statusChange(patient, updated))
                .block();
    }
}