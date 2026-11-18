package cl.duoc.ms_vidasalud_report.messaging.dto;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El JSON que publica appointments (sin headers de tipo) se lee con la configuración de application.yaml. */
class EventoAtencionJsonTest {

    private static final String JSON = """
            {
              "version": 1,
              "eventId": "0b6c1d4e-2f3a-4b5c-8d9e-0f1a2b3c4d5e",
              "type": "APPOINTMENT_STATUS_CHANGED",
              "timestamp": "2026-11-10T15:04:05.123Z",
              "correlationId": "atencion-42",
              "traceId": "abc123",
              "campoNuevo": "se ignora",
              "payload": {
                "atencionId": 42,
                "paciente": "Ana",
                "servicio": "Dental",
                "box": "Box 3",
                "fechaHora": "2026-11-20T10:30:00",
                "estado": "CONFIRMADA",
                "estadoAnterior": "SOLICITADA",
                "otroCampo": 1,
                "actor": {"id": "oid-op", "nombre": "Olga Operadora", "rol": "Operador", "email": "x"}
              }
            }
            """;

    @Test
    void deserializaElFormatoDelProductorIgnorandoCamposDesconocidos() {
        try (var deserializer = new JacksonJsonDeserializer<EventoAtencion>()) {
            deserializer.configure(Map.of(
                    "spring.json.value.default.type", EventoAtencion.class.getName(),
                    "spring.json.use.type.headers", false), false);

            EventoAtencion evento = deserializer.deserialize("appointments.events",
                    JSON.getBytes(StandardCharsets.UTF_8));

            assertThat(evento.version()).isEqualTo(1);
            assertThat(evento.eventId()).isEqualTo("0b6c1d4e-2f3a-4b5c-8d9e-0f1a2b3c4d5e");
            assertThat(evento.type()).isEqualTo(EventoAtencion.TYPE_ESTADO_CAMBIADO);
            assertThat(evento.timestamp()).isEqualTo(Instant.parse("2026-11-10T15:04:05.123Z"));
            assertThat(evento.traceId()).isEqualTo("abc123");
            DatosEventoAtencion payload = evento.payload();
            assertThat(payload.atencionId()).isEqualTo(42L);
            assertThat(payload.servicio()).isEqualTo("Dental");
            assertThat(payload.fechaHora()).isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 30));
            assertThat(payload.estado()).isEqualTo("CONFIRMADA");
            assertThat(payload.estadoAnterior()).isEqualTo("SOLICITADA");
            assertThat(payload.actor()).isEqualTo(new ActorEvento("oid-op", "Olga Operadora", "Operador"));
        }
    }
}
