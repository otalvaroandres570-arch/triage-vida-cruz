package com.vida_cruz.triage_service.infraestructure.adapters.alerting;

public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message) {
        super(message);
    }
}