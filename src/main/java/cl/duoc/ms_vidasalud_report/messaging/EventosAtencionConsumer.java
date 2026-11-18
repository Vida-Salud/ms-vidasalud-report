package cl.duoc.ms_vidasalud_report.messaging;

import cl.duoc.ms_vidasalud_report.exception.EventoInvalidoException;
import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;
import cl.duoc.ms_vidasalud_report.service.ProyeccionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consume {@code appointments.events}, valida el contrato y delega en {@link ProyeccionService}.
 * <p>
 * Los errores se relanzan: el {@code DefaultErrorHandler} de {@code KafkaConsumidorConfig} decide
 * si reintentar o mandar el mensaje al DLT.
 */
@Component
public class EventosAtencionConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventosAtencionConsumer.class);
    private static final String MDC_TRACE_ID = "traceId";

    private final ProyeccionService proyeccionService;
    private final Validator validator;

    public EventosAtencionConsumer(ProyeccionService proyeccionService, Validator validator) {
        this.proyeccionService = proyeccionService;
        this.validator = validator;
    }

    @KafkaListener(topics = "${vidasalud.kafka.topico-eventos}")
    public void consumir(ConsumerRecord<String, EventoAtencion> registro) {
        EventoAtencion evento = registro.value();
        // El traceId del productor queda en el log de este servicio (logging.pattern.correlation).
        try (var ignorado = MDC.putCloseable(MDC_TRACE_ID, evento != null ? evento.traceId() : null)) {
            log.info("Evento recibido [topic={}, partition={}, offset={}, eventId={}, atencionId={}]",
                    registro.topic(), registro.partition(), registro.offset(),
                    evento != null ? evento.eventId() : null,
                    evento != null && evento.payload() != null ? evento.payload().atencionId() : null);

            validar(evento);
            proyeccionService.aplicar(evento);
        }
    }

    private void validar(EventoAtencion evento) {
        if (evento == null) {
            throw new EventoInvalidoException("Mensaje sin contenido");
        }
        Set<ConstraintViolation<EventoAtencion>> errores = validator.validate(evento);
        if (!errores.isEmpty()) {
            String detalle = errores.stream()
                    .map(e -> e.getPropertyPath() + ": " + e.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EventoInvalidoException("Evento inválido [eventId=%s]: %s".formatted(evento.eventId(), detalle));
        }
    }
}
