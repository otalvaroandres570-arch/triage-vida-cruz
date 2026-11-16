package com.vida_cruz.triage_service.infraestructure.adapters.messaging;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Map;

public final class KafkaSenderFactory {

    private KafkaSenderFactory() {
    }

    public static KafkaSender<String, String> create(String bootstrapServers) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.CLIENT_ID_CONFIG, "triage-service-producer",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                // Durability: wait for all in-sync replicas and avoid duplicates or reordering on retries.
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                // Bounded waiting: a broker outage must fail fast instead of hanging the request for minutes.
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);

        return KafkaSender.create(SenderOptions.create(config));
    }
}