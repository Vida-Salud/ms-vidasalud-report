package cl.duoc.ms_vidasalud_report.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Payload del evento. estadoAnterior es null en APPOINTMENT_CREATED. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosEventoAtencion(
        @NotNull Long atencionId,
        String paciente,
        @NotBlank String servicio,
        String box,
        LocalDateTime fechaHora,
        @NotBlank String estado,
        String estadoAnterior,
        ActorEvento actor) {
}
