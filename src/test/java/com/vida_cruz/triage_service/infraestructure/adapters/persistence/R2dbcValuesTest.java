package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.model.enums.PatientStatus;
import io.r2dbc.spi.Parameter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class R2dbcValuesTest {

    private static final Instant INSTANT = Instant.parse("2026-11-12T15:00:00Z");

    @Test
    void shouldBindAPresentValueAsIs() {
        Parameter parameter = R2dbcValues.nullable("EN_CONSULTA", String.class);

        assertEquals("EN_CONSULTA", parameter.getValue());
        assertEquals(String.class, parameter.getType().getJavaType());
    }

    @Test
    void shouldBindATypedNullWhenTheValueIsMissing() {
        Parameter parameter = R2dbcValues.nullable(null, OffsetDateTime.class);

        assertNull(parameter.getValue());
        assertEquals(OffsetDateTime.class, parameter.getType().getJavaType());
    }

    @Test
    void shouldConvertBeforeBindingWhenTheValueIsPresent() {
        Parameter parameter = R2dbcValues.nullable(PatientStatus.ALTA, PatientStatus::name, String.class);

        assertEquals("ALTA", parameter.getValue());
    }

    @Test
    void shouldNotCallTheConverterWhenTheValueIsMissing() {
        Parameter parameter = R2dbcValues.nullable((Instant) null, R2dbcValues::toOffset, OffsetDateTime.class);

        assertNull(parameter.getValue());
        assertEquals(OffsetDateTime.class, parameter.getType().getJavaType());
    }

    @Test
    void shouldConvertInstantsToUtcOffsetsAndBack() {
        OffsetDateTime offset = R2dbcValues.toOffset(INSTANT);

        assertEquals(ZoneOffset.UTC, offset.getOffset());
        assertEquals(INSTANT, R2dbcValues.toInstantOrNull(offset));
        assertNull(R2dbcValues.toInstantOrNull(null));
    }

    @Test
    void shouldMapOnlyPresentValues() {
        assertEquals(PatientStatus.REGISTRADO, R2dbcValues.mapOrNull("REGISTRADO", PatientStatus::valueOf));
        assertNull(R2dbcValues.mapOrNull(null, PatientStatus::valueOf));
    }
}
