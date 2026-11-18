package cl.duoc.ms_vidasalud_report.messaging;

import cl.duoc.ms_vidasalud_report.exception.EventoInvalidoException;
import cl.duoc.ms_vidasalud_report.messaging.dto.DatosEventoAtencion;
import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;
import cl.duoc.ms_vidasalud_report.service.ProyeccionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventosAtencionConsumerTest {

    private static final String TOPICO = "appointments.events";
    private static ValidatorFactory validatorFactory;

    @Mock ProyeccionService proyeccionService;
    EventosAtencionConsumer consumer;

    @BeforeAll
    static void crearValidador() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void cerrarValidador() {
        validatorFactory.close();
    }

    @BeforeEach
    void crearConsumer() {
        Validator validator = validatorFactory.getValidator();
        consumer = new EventosAtencionConsumer(proyeccionService, validator);
    }

    @Test
    void eventoInvalidoLanzaExcepcionConElDetalleYNoLlamaAlServicio() {
        var evento = new EventoAtencion(1, " ", EventoAtencion.TYPE_CREADA, Instant.parse("2026-11-10T12:00:00Z"),
                null, null,
                new DatosEventoAtencion(null, "Ana", "Dental", null, null, "", null, null));

        assertThatThrownBy(() -> consumer.consumir(registro(evento)))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("eventId")
                .hasMessageContaining("payload.atencionId")
                .hasMessageContaining("payload.estado");
        verifyNoInteractions(proyeccionService);
    }

    @Test
    void eventoSinPayloadEsInvalido() {
        var evento = new EventoAtencion(1, "ev-1", EventoAtencion.TYPE_CREADA,
                Instant.parse("2026-11-10T12:00:00Z"), null, null, null);

        assertThatThrownBy(() -> consumer.consumir(registro(evento)))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("payload");
        verifyNoInteractions(proyeccionService);
    }

    @Test
    void mensajeVacioEsInvalido() {
        assertThatThrownBy(() -> consumer.consumir(registro(null)))
                .isInstanceOf(EventoInvalidoException.class);
        verifyNoInteractions(proyeccionService);
    }

    @Test
    void eventoValidoSeDelegaAlServicio() {
        var evento = new EventoAtencion(1, "ev-1", EventoAtencion.TYPE_CREADA,
                Instant.parse("2026-11-10T12:00:00Z"), "atencion-7", "trace-1",
                new DatosEventoAtencion(7L, "Ana", "Dental", "Box 3", null, "SOLICITADA", null, null));

        consumer.consumir(registro(evento));

        verify(proyeccionService).aplicar(evento);
    }

    private static ConsumerRecord<String, EventoAtencion> registro(EventoAtencion evento) {
        return new ConsumerRecord<>(TOPICO, 1, 42L, "7", evento);
    }
}
