package com.vida_cruz.triage_service.infraestructure.adapters.persistence;

import com.vida_cruz.triage_service.domain.spi.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcTransactionRunner implements TransactionRunner {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> inTransaction(Mono<T> operation) {
        return transactionalOperator.transactional(operation);
    }
}