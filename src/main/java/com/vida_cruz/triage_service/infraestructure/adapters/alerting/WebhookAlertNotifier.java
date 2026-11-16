package com.vida_cruz.triage_service.infraestructure.adapters.alerting;

import com.vida_cruz.triage_service.domain.model.EscalationAlert;
import com.vida_cruz.triage_service.domain.spi.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** Posts the alert as JSON to a webhook. The URL is a secret: it is never logged nor included in errors. */
@Slf4j(topic = "escalation-alerts")
public class WebhookAlertNotifier implements AlertNotifier {

    private final WebClient client;
    private final URI uri;
    private final Duration timeout;

    public WebhookAlertNotifier(WebClient client, String url, Duration timeout) {
        this.client = Objects.requireNonNull(client);
        this.uri = URI.create(Objects.requireNonNull(url));
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Override
    public Mono<Void> notify(EscalationAlert alert) {
        return client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(WebhookPayload.from(alert))
                .retrieve()
                .toBodilessEntity()
                .timeout(timeout)
                .doOnSuccess(response -> log.info("Escalation alert delivered for patient {}", alert.patientId()))
                .then()
                .onErrorMap(WebClientResponseException.class,
                        error -> new WebhookDeliveryException("Webhook answered HTTP " + error.getStatusCode().value()))
                .onErrorMap(TimeoutException.class,
                        error -> new WebhookDeliveryException("Webhook timed out after " + timeout))
                .onErrorMap(error -> !(error instanceof WebhookDeliveryException),
                        error -> new WebhookDeliveryException("Webhook unreachable: " + error.getClass().getSimpleName()))
                .doOnError(error -> log.warn("Escalation alert for patient {} not delivered: {}",
                        alert.patientId(), error.getMessage()));
    }

    record WebhookPayload(String text, AlertData alert) {

        record AlertData(String type, String patientId, String level, int slaMinutes,
                         long waitedMinutes, String escalatedAt) {
        }

        static WebhookPayload from(EscalationAlert alert) {
            return new WebhookPayload(alert.message(), new AlertData(
                    "paciente_escalado",
                    alert.patientId().toString(),
                    alert.level().name(),
                    alert.slaMinutes(),
                    alert.waitedMinutes(),
                    alert.escalatedAt().toString()));
        }
    }
}