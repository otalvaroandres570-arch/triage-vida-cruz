# Servicio de Triage

Registra a los pacientes, los clasifica con reglas clínicas objetivas, gestiona sus estados, vigila los SLA y escala a quien
los supera, genera el reporte de operación y avisa al equipo médico. Publica sus eventos en el topic `triaje.eventos` de Kafka.

La documentación completa (cómo ejecutar todo con un solo comando, arquitectura, supuestos y ADR) está en el repositorio
[`vidacruz-platform`](https://github.com/otalvaroandres570-arch/vidacruz-platform).

## Ejecutar y probar

```
./gradlew bootRun     # necesita PostgreSQL y Kafka (ver la plataforma) y las variables de abajo
./gradlew test        # requiere Docker (Testcontainers)
```

Imagen de contenedor (compilación en dos etapas, se ejecuta sin privilegios):

```
docker build -t vidacruz/triage-service:local .
```

## Configuración (variables de entorno)

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_PASSWORD` | (obligatoria) | Contraseña de PostgreSQL |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | `localhost`, `5432`, `triage_db`, `triage_user` | Conexión a PostgreSQL |
| `TRIAGE_SECURITY_APIKEYS` | (obligatoria) | API keys aceptadas, separadas por comas, de 16 o más caracteres. Sin ellas el servicio no arranca |
| `TRIAGE_KAFKA_BOOTSTRAPSERVERS`, `TRIAGE_KAFKA_TOPIC` | `localhost:9092`, `triaje.eventos` | Kafka |
| `TRIAGE_SLA_CRITICO`, `..._URGENTE`, `..._URGENTE2`, `..._NOURGENTE`, `..._NOURGENTE2` | 5, 10, 30, 60, 120 | SLA en minutos por nivel |
| `TRIAGE_SLAMONITOR_INTERVAL` | `10s` | Cada cuánto se evalúan los SLA |
| `TRIAGE_ALERTS_WEBHOOK_URL` | vacía | Webhook de alertas (secreto). Vacía, las alertas van al log |
| `SPRING_PROFILES_ACTIVE` | | `json` para logs en JSON |

Puerto: `8081`. Probes públicos: `/actuator/health/liveness` y `/actuator/health/readiness`.
Métricas (protegidas con la API key): `/actuator/prometheus`.

## API

Todo exige la cabecera `X-API-Key`, salvo los probes de salud.

| Método y ruta | Descripción |
|---|---|
| `POST /triage/pacientes` | Registra y clasifica al paciente (201; 400 con el campo inválido) |
| `PATCH /triage/pacientes/{id}/estado` | Cambia el estado (200; 404; 409 si la transición no es válida o hubo un cambio concurrente) |
| `GET /triage/pacientes/{id}` | Detalle, flag `escalado` y tiempo en el estado actual (200; 404) |
| `GET /triage/reportes/operacion?desde=...&hasta=...` | Reporte por hora, fechas ISO 8601 (200; 400) |

## Estructura (arquitectura hexagonal)

```
domain/            modelo, casos de uso y puertos, sin dependencias de Spring
infraestructure/
  adapters/        PostgreSQL (R2DBC), Kafka, webhook de alertas, métricas
  entrypoints/     controladores REST, tareas periódicas, filtro de API key
  configuration/   ensamblado de los beans y propiedades configurables
```

Las dependencias apuntan siempre hacia el dominio. Las decisiones de diseño (outbox transaccional, monitor de SLA, alertas,
autenticación, observabilidad...) están razonadas en los ADR del repositorio de la plataforma.