package com.vida_cruz.triage_service.domain.spi;

import reactor.core.publisher.Mono;

public interface TransactionRunner {

    /**
     * Runs the operation in a transaction: everything it writes is committed together,
     * or rolled back if it fails. Joins an existing transaction if there is one.
     */
    <T> Mono<T> inTransaction(Mono<T> operation);
}