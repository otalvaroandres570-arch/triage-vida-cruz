package com.vida_cruz.triage_service.domain.usecase;

import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import reactor.core.publisher.Mono;

/** No real transaction in unit tests: atomicity is verified in the integration tests. */
class PassThroughTransactionRunner implements TransactionRunner {

    @Override
    public <T> Mono<T> inTransaction(Mono<T> operation) {
        return operation;
    }
}