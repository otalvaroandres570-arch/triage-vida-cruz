package com.vida_cruz.triage_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DatabaseMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void shouldCreateSchemaWithFlywayMigration() {
        Mono<Long> tableCount = databaseClient.sql("""
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('patient', 'patient_audit')
                        """)
                .map(row -> row.get(0, Long.class))
                .one();

        StepVerifier.create(tableCount)
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void shouldAcceptValidPatient() {
        StepVerifier.create(insertPatient(99, 120, 80))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void shouldRejectSpo2OutOfRange() {
        StepVerifier.create(insertPatient(101, 120, 80))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    @Test
    void shouldRejectDiastolicNotLowerThanSystolic() {
        StepVerifier.create(insertPatient(99, 100, 100))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }

    private Mono<Long> insertPatient(int spo2, int systolic, int diastolic) {
        String sql = """
                INSERT INTO patient (id, full_name, age, heart_rate, systolic_bp, diastolic_bp, spo2,
                                     temperature, pain, gcs, chief_complaint, triage_level, sla_minutes,
                                     status, registered_at, status_changed_at)
                VALUES (gen_random_uuid(), 'Test Patient', 40, 80, %d, %d, %d,
                        36.5, 2, 15, 'Test', 4, 60, 'REGISTRADO', now(), now())
                """.formatted(systolic, diastolic, spo2);

        return databaseClient.sql(sql).fetch().rowsUpdated();
    }
}