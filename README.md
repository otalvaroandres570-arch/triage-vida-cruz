# Servicio de Triage

Registra a los pacientes, los clasifica con reglas clínicas objetivas, gestiona sus estados, vigila los SLA y escala a quien
los supera, genera el reporte de operación y avisa al equipo médico. Publica sus eventos en el topic `triaje.eventos` de Kafka.

La documentación completa (cómo ejecutar todo con un comando, arquitectura, supuestos y ADR) está en el repositorio
[`vidacruz-platform`](https://github.com/otalvaroandres570-arch/vidacruz-platform).

## Ejecutar y probar

./gradlew bootRun # necesita PostgreSQL y Kafka (ver la plataforma) y las variables de abajo
./gradlew test # requiere Docker (Testcontainers)

## Configuración (variables de entorno)

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_PASSWORD` | (obligatoria) | Contraseña de PostgreSQL |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | `localhost`, `5432`, `triage_db`, `triage_user` | Conexión a PostgreSQL |
| `TRIAGE_SECURITY_APIKEYS` | (obligatoria) | API keys aceptadas, separadas por comas, de 16 o más caracteres |
| `TRIAGE_KAFKA_BOOTSTRAPSERVERS`, `TRIAGE_KAFKA_TOPIC` | `localhost:9092`, `triaje.eventos` | Kafka |
| `TRIAGE_SLA_CRITICO`, `..._URGENTE`, `..._URGENTE2`, `..._NOURGENTE`, `..._NOURGENTE2` | 5, 10, 30, 60, 120 | SLA en minutos por nivel |
| `TRIAGE_SLAMONITOR_INTERVAL` | `10s` | Cada cuánto se evalúan los SLA |
| `TRIAGE_ALERTS_WEBHOOK_URL` | vacía | Webhook de alertas (secreto); vacía, las alertas van al log |
| `SPRING_PROFILES_ACTIVE` | | `json` para logs en JSON |

Puerto: `8081`. Probes públicos: `/actuator/health/liveness` y `/actuator/health/readiness`.