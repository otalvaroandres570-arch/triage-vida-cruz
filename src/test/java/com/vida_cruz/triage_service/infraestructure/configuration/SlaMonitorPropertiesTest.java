package com.vida_cruz.triage_service.infraestructure.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaMonitorPropertiesTest {

    @Test
    void shouldAcceptValidValues() {
        assertDoesNotThrow(() -> new SlaMonitorProperties(true, Duration.ofSeconds(10), 100));
    }

    @Test
    void shouldRejectZeroInterval() {
        assertThrows(IllegalArgumentException.class, () -> new SlaMonitorProperties(true, Duration.ZERO, 100));
    }

    @Test
    void shouldRejectNegativeInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlaMonitorProperties(true, Duration.ofSeconds(-1), 100));
    }

    @Test
    void shouldRejectMissingInterval() {
        assertThrows(IllegalArgumentException.class, () -> new SlaMonitorProperties(true, null, 100));
    }

    @ParameterizedTest(name = "batch size {0} is rejected")
    @ValueSource(ints = {0, -5})
    void shouldRejectNonPositiveBatchSize(int batchSize) {
        assertThrows(IllegalArgumentException.class,
                () -> new SlaMonitorProperties(true, Duration.ofSeconds(10), batchSize));
    }
}