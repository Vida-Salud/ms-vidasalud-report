package cl.duoc.ms_vidasalud_report.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Payload del evento. estadoAnterior es null en APPOINTMENT_CREATED. El evento trae además
 * {@code origen} (servicio e IP), que la reportería no usa y se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosEventoAtencion(
        @NotNull Long atencionId,
        @NotBlank String servicio,
        String box,
        LocalDateTime fechaHora,
        @NotBlank String estado,
        String estadoAnterior,
        ActorEvento actor) {
}
