package com.vida_cruz.triage_service.domain.exceptions;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String from, String to) {
        super("Status transition not allowed: from %s to %s".formatted(from, to));
    }
}
