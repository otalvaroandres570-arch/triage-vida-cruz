package com.vida_cruz.triage_service.infraestructure.entrypoints.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAuthenticationFilterTest {

    private static final String KEY = "primary-key-0123456789";
    private static final String NEXT_KEY = "rotated-key-0123456789";

    private final ApiKeyAuthenticationFilter filter =
            new ApiKeyAuthenticationFilter(List.of(KEY, NEXT_KEY), new ObjectMapper().findAndRegisterModules());

    private record Outcome(MockServerWebExchange exchange, boolean passed) {
    }

    @ParameterizedTest(name = "key {0} is accepted")
    @ValueSource(strings = {KEY, NEXT_KEY})
    void shouldLetThrpZEAWYtiB6bJ16NuLbGCc6CZ6jJdKfb63(String key) {
        assertTrue(call("/triage/pacientes", key).passed());
    }

    @ParameterizedTest(name = "[{index}] key {0} is rejected")
    @NullSource
    @ValueSource(strings = {"", "  ", "wrong-key-0123456789", KEY + " extra"})
    void shouldRejectMissingOrWrongKeys(String key) {
        Outcome outcome = call("/triage/pacientes", key);

        assertFalse(outcome.passed());
        Assertions.assertNotNull(outcome.exchange().getResponse().getStatusCode());
        assertEquals(401, outcome.exchange().getResponse().getStatusCode().value());
    }

    @ParameterizedTest(name = "{0} is public")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/info"})
    void shouldKeepTheHealthProbesPublic(String path) {
        assertTrue(call(path, null).passed());
    }

    @ParameterizedTest(name = "{0} is not public")
    @ValueSource(strings = {"/actuator/health-secret", "/actuator/healthz", "/actuator/env", "/triage/pacientes"})
    void shouldNotTreatAnythingElseAsPublic(String path) {
        assertFalse(call(path, null).passed());
    }

    @Test
    void shouldAnswerWithAJsonErrorAndAChallengeWithoutRevealingOrEchoingAnything() {
        Outcome outcome = call("/triage/pacientes", "wrong-key-0123456789");

        assertEquals("ApiKey", outcome.exchange().getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
        String body = outcome.exchange().getResponse().getBodyAsString().block();
        Assertions.assertNotNull(body);
        assertTrue(body.contains("\"code\":\"UNAUTHORIZED\""));
        assertFalse(body.contains("wrong-key"));
    }

    private Outcome call(String path, String key) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        Optional.ofNullable(key).ifPresent(value -> request.header(ApiKeyAuthenticationFilter.HEADER, value));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean passed = new AtomicBoolean(false);

        filter.filter(exchange, chainExchange -> Mono.fromRunnable(() -> passed.set(true))).block();
        return new Outcome(exchange, passed.get());
    }
}