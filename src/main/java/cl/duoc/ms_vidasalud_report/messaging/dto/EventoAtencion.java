package cl.duoc.ms_vidasalud_report.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Envelope de los eventos que ms-vidasalud-appointments publica en {@code appointments.events}.
 * Copia propia del contrato: este servicio no depende de las clases del productor. Se ignoran los
 * campos desconocidos para que el productor pueda agregar datos sin romper al consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoAtencion(
        int version,
        @NotBlank String eventId,
        @NotBlank String type,
        @NotNull Instant timestamp,
        String correlationId,
        String traceId,
        @NotNull @Valid DatosEventoAtencion payload) {

    public static final String TYPE_CREADA = "APPOINTMENT_CREATED";
    public static final String TYPE_ESTADO_CAMBIADO = "APPOINTMENT_STATUS_CHANGED";
}
