package com.vida_cruz.triage_service.domain.model;

import java.time.Duration;
import java.util.Optional;

public record RetryBackoff(Duration base, Duration max) {

    private static final int MAX_DOUBLINGS = 20;

    public RetryBackoff {
        Duration start = Optional.ofNullable(base)
                .filter(value -> !value.isZero() && !value.isNegative())
                .orElseThrow(() -> new IllegalArgumentException("base must be positive"));
        max = Optional.ofNullable(max)
                .filter(value -> value.compareTo(start) >= 0)
                .orElseThrow(() -> new IllegalArgumentException("max must not be shorter than base"));
    }

    /** Wait after the given number of failed attempts: base, 2x base, 4x base... never more than max. */
    public Duration delayAfter(int attempts) {
        Duration exponential = base.multipliedBy(1L << Math.clamp(attempts - 1, 0, MAX_DOUBLINGS));
        return exponential.compareTo(max) > 0 ? max : exponential;
    }
}