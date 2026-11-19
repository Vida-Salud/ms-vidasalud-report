# ms-vidasalud-report

Microservicio de reportería de VidaSalud. Consume los eventos de atenciones que publica
`ms-vidasalud-appointments` en Kafka, construye su propia proyección en Oracle y expone los
KPIs por API REST.

No consulta la base de datos de `appointments` ni le hace peticiones: todo lo que sabe lo
aprendió de los eventos.

## Cómo funciona

```
appointments  ──►  Kafka: appointments.events  ──►  report  ──►  Oracle (proyección)  ──►  GET /api/v1/report/kpis
```

1. `EventosAtencionConsumer` escucha el tópico con el consumer group `report-kpis`.
2. Valida el evento y descarta los repetidos comparando el `eventId` (Kafka entrega al menos
   una vez, así que un evento puede llegar más de una vez).
3. `ProyeccionServiceImpl` actualiza, en una sola transacción, el registro del evento y la
   proyección de la atención.
4. Los endpoints calculan los KPIs sobre esa proyección, con consultas JPQL.

Si un evento falla, se reintenta 3 veces con 1 segundo de espera. Si sigue fallando, se va al
tópico `appointments.events.DLT` y el consumidor continúa con el siguiente, para que un mensaje
malo no bloquee la partición. Los eventos mal formados o inválidos no se reintentan: van directo
al DLT, porque reintentarlos daría el mismo error.

## Requisitos

- Java 21
- Oracle accesible (local o en contenedor)
- Clúster Kafka levantado (`infra/kafka/compose.yml`: 3 Zookeeper + 3 brokers + Kafka UI)
- Una aplicación registrada en Azure AD con el app role `Admin`

## Variables de entorno

Copia `.env.example` a `.env` y complétalo. El `.env` no se versiona.

| Variable | Para qué | Ejemplo |
|---|---|---|
| `AZURE_TENANT_ID` | Tenant de Azure AD que emite los tokens | `9832f4f3-...` |
| `AZURE_CLIENT_ID` | Application (client) ID de la app registrada | |
| `DB_HOST` | Host de Oracle | `localhost` |
| `DB_PORT` | Puerto de Oracle | `1521` |
| `DB_SERVICE` | Nombre del servicio | `FREEPDB1` |
| `DB_USERNAME` | Usuario de la base | `vidasalud` |
| `DB_PASSWORD` | Contraseña | |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers del clúster, separados por coma | `localhost:9092,localhost:9093,localhost:9094` |

Desde un contenedor, los brokers no son `localhost`: hay que apuntar al host
(`host.docker.internal:9092,...` en local).

## Cómo levantarlo

**Desde el IDE o la terminal**

```bash
./mvnw spring-boot:run
```

Queda en `http://localhost:8094`.

**Con Docker, junto al resto del sistema**

```bash
cd infra/apps
docker compose up -d --build report
```

**Correr los tests**

```bash
./mvnw verify
```

## Endpoints

Todos exigen un JWT de Azure AD con el app role **Admin**. Sin token responde 401; con otro
rol, 403.

### `GET /api/v1/report/kpis`

| Parámetro | Valores | Por defecto |
|---|---|---|
| `range` | `last24h`, `last7d`, `last30d` | `last24h` |

Devuelve el total de atenciones del rango, el tiempo de espera promedio en minutos, el conteo
por hora y el conteo por estado.

```json
{
  "rango": "last24h",
  "desde": "2026-11-18T04:00:00Z",
  "hasta": "2026-11-19T04:00:00Z",
  "totalAtenciones": 2,
  "tiempoEsperaPromedioMinutos": 12.5,
  "atencionesPorHora": [],
  "atencionesPorEstado": []
}
```

`tiempoEsperaPromedioMinutos` viene en `null` si en el rango ninguna atención pasó de
`EN_ESPERA` a `EN_ATENCION`.

### `GET /api/v1/report/top-services`

| Parámetro | Valores | Por defecto |
|---|---|---|
| `range` | `last24h`, `last7d`, `last30d` | `last7d` |
| `limit` | entre 1 y 20 | `5` |

Devuelve las prestaciones más solicitadas del rango.

Un `range` que no esté en la lista responde 400 con los valores permitidos.

## Configuración de Kafka

| | Valor | Por qué |
|---|---|---|
| Tópico que consume | `appointments.events` | Lo declara `appointments`, que es su dueño |
| Consumer group | `report-kpis` | Lleva su propio offset, independiente del de `audit` |
| Concurrency | 3 | Una hebra por partición del tópico |
| `auto-offset-reset` | `earliest` | Un grupo nuevo lee desde el principio y reconstruye la proyección |
| DLT | `appointments.events.DLT` | 3 particiones, 3 réplicas, retención 14 días, `min.insync.replicas=2` |

El JSON viaja sin encabezados de tipo: el tipo destino se fija en la configuración del
consumidor (`spring.json.value.default.type`). Así este servicio no depende de las clases de
`appointments`.

## Tablas

| Tabla | Para qué |
|---|---|
| `REPORT_EVENTO` | Un registro por `eventId` procesado. Es la base de la idempotencia |
| `REPORT_ATENCION` | Estado actual de cada atención y la fecha en que entró a cada estado. De aquí salen todos los KPIs |

Las crea Hibernate al arrancar (`ddl-auto: update`).

## Estructura

```
config/       Seguridad, Clock y el conversor del parámetro range
controller/   Endpoints y manejo de errores HTTP
dto/          Respuestas de la API
messaging/    Consumidor Kafka, configuración de errores/DLT y DTOs del evento
model/        Entidades JPA y el enum de rangos
repository/   Consultas JPQL de los KPIs
service/      Proyección de eventos y cálculo de KPIs
```
