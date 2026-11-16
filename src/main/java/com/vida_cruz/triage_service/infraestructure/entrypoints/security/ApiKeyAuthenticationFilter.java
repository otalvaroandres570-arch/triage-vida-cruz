package com.vida_cruz.triage_service.infraestructure.entrypoints.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vida_cruz.triage_service.infraestructure.entrypoints.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Requires a valid API key in every request except the health probes. */
@Slf4j
public class ApiKeyAuthenticationFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-API-Key";

    private final List<byte[]> acceptedDigests;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(List<String> apiKeys, ObjectMapper objectMapper) {
        this.acceptedDigests = apiKeys.stream().map(ApiKeyAuthenticationFilter::digest).toList();
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return isPublic(exchange.getRequest().getPath().value()) || hasAcceptedKey(exchange)
                ? chain.filter(exchange)
                : reject(exchange);
    }

    /** Only the health probes (needed by Kubernetes) and the info endpoint: nothing that merely starts like them. */
    private static boolean isPublic(String path) {
        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info");
    }

    /** Compares digests, and against every accepted key without stopping at the first match. */
    private boolean hasAcceptedKey(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HEADER))
                .map(ApiKeyAuthenticationFilter::digest)
                .map(presented -> acceptedDigests.stream()
                        .map(accepted -> MessageDigest.isEqual(accepted, presented))
                        .reduce(false, Boolean::logicalOr))
                .orElse(false);
    }

    /** The answer does not say whether the key is missing or wrong, and never echoes what was sent. */
    private Mono<Void> reject(ServerWebExchange exchange) {
        log.warn("Rejected {} {}: API key missing or invalid",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");

        return response.writeWith(Mono.fromCallable(() -> objectMapper.writeValueAsBytes(ErrorResponse.of(
                        401, "UNAUTHORIZED", "Se requiere una API key válida en la cabecera " + HEADER)))
                .map(bytes -> response.bufferFactory().wrap(bytes)));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}