package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;

/** Pure conversions between domain values and R2DBC values, so statements can be bound in a single chain. */
final class R2dbcValues {

    private R2dbcValues() {
    }

    /** A bind value that may be null: R2DBC needs the column type to bind a typed NULL. */
    static <T> Parameter nullable(T value, Class<? super T> type) {
        return Optional.ofNullable(value)
                .map(Parameters::in)
                .orElseGet(() -> Parameters.in(type));
    }

    /** Same as {@link #nullable} but converting the value first (e.g. Instant -> OffsetDateTime). */
    static <S, T> Parameter nullable(S value, Function<S, T> converter, Class<T> type) {
        return nullable(Optional.ofNullable(value).map(converter).orElse(null), type);
    }

    static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    static Instant toInstantOrNull(OffsetDateTime value) {
        return Optional.ofNullable(value).map(OffsetDateTime::toInstant).orElse(null);
    }

    static <T> T mapOrNull(String value, Function<String, T> mapper) {
        return Optional.ofNullable(value).map(mapper).orElse(null);
    }
}
