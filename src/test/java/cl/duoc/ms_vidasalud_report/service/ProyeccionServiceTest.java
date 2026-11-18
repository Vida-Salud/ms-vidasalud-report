package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.messaging.dto.ActorEvento;
import cl.duoc.ms_vidasalud_report.messaging.dto.DatosEventoAtencion;
import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;
import cl.duoc.ms_vidasalud_report.model.EventoProcesado;
import cl.duoc.ms_vidasalud_report.model.ProyeccionAtencion;
import cl.duoc.ms_vidasalud_report.repository.EventoProcesadoRepository;
import cl.duoc.ms_vidasalud_report.repository.ProyeccionAtencionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionServiceTest {

    private static final Instant T1 = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-10T12:05:00Z");
    private static final Instant T3 = Instant.parse("2026-11-10T12:10:00Z");

    @Mock EventoProcesadoRepository eventoRepository;
    @Mock ProyeccionAtencionRepository proyeccionRepository;
    ProyeccionService service;

    @BeforeEach
    void crearServicio() {
        service = new ProyeccionServiceImpl(eventoRepository, proyeccionRepository);
    }

    @Test
    void creacionGuardaElEventoYUnaProyeccionNueva() {
        when(proyeccionRepository.findById(7L)).thenReturn(Optional.empty());

        service.aplicar(evento("ev-1", EventoAtencion.TYPE_CREADA, 7L, "SOLICITADA", null, T1));

        var eventoCaptor = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(eventoRepository).save(eventoCaptor.capture());
        EventoProcesado guardado = eventoCaptor.getValue();
        assertThat(guardado.getEventId()).isEqualTo("ev-1");
        assertThat(guardado.getAtencionId()).isEqualTo(7L);
        assertThat(guardado.getType()).isEqualTo(EventoAtencion.TYPE_CREADA);
        assertThat(guardado.getEstado()).isEqualTo("SOLICITADA");
        assertThat(guardado.getEstadoAnterior()).isNull();
        assertThat(guardado.getServicio()).isEqualTo("Dental");
        assertThat(guardado.getOcurridoEn()).isEqualTo(T1);

        ProyeccionAtencion proyeccion = proyeccionGuardada();
        assertThat(proyeccion.getAtencionId()).isEqualTo(7L);
        assertThat(proyeccion.getServicio()).isEqualTo("Dental");
        assertThat(proyeccion.getEstadoActual()).isEqualTo("SOLICITADA");
        assertThat(proyeccion.getCreadaEn()).isEqualTo(T1);
        assertThat(proyeccion.getConfirmadaEn()).isNull();
        assertThat(proyeccion.getActualizadaEn()).isEqualTo(T1);
    }

    @Test
    void cambioDeEstadoActualizaElEstadoYMarcaSuFecha() {
        when(proyeccionRepository.findById(7L)).thenReturn(Optional.of(proyeccion(7L, "SOLICITADA", T1)));

        service.aplicar(evento("ev-2", EventoAtencion.TYPE_ESTADO_CAMBIADO, 7L, "CONFIRMADA", "SOLICITADA", T2));

        ProyeccionAtencion proyeccion = proyeccionGuardada();
        assertThat(proyeccion.getEstadoActual()).isEqualTo("CONFIRMADA");
        assertThat(proyeccion.getCreadaEn()).isEqualTo(T1);
        assertThat(proyeccion.getConfirmadaEn()).isEqualTo(T2);
        assertThat(proyeccion.getActualizadaEn()).isEqualTo(T2);
    }

    @ParameterizedTest
    @CsvSource({
            "SOLICITADA, creadaEn",
            "CONFIRMADA, confirmadaEn",
            "EN_ESPERA, enEsperaEn",
            "EN_ATENCION, enAtencionEn",
            "CERRADA, cerradaEn",
            "CANCELADA, canceladaEn"})
    void cadaEstadoMarcaSuPropiaFecha(String estado, String campo) {
        when(proyeccionRepository.findById(7L)).thenReturn(Optional.empty());

        service.aplicar(evento("ev-" + estado, EventoAtencion.TYPE_ESTADO_CAMBIADO, 7L, estado, null, T2));

        ProyeccionAtencion proyeccion = proyeccionGuardada();
        assertThat(proyeccion.getEstadoActual()).isEqualTo(estado);
        assertThat(proyeccion).extracting(campo).isEqualTo(T2);
    }

    @Test
    void eventoDuplicadoSeIgnora() {
        when(eventoRepository.existsById("ev-1")).thenReturn(true);

        service.aplicar(evento("ev-1", EventoAtencion.TYPE_CREADA, 7L, "SOLICITADA", null, T1));

        verify(eventoRepository, never()).save(any());
        verifyNoInteractions(proyeccionRepository);
    }

    @Test
    void eventoAntiguoRegistraSuFechaPeroNoPisaElEstadoActual() {
        when(proyeccionRepository.findById(7L)).thenReturn(Optional.of(proyeccion(7L, "EN_ESPERA", T3)));

        service.aplicar(evento("ev-2", EventoAtencion.TYPE_ESTADO_CAMBIADO, 7L, "CONFIRMADA", "SOLICITADA", T2));

        verify(eventoRepository).save(any(EventoProcesado.class));
        ProyeccionAtencion proyeccion = proyeccionGuardada();
        assertThat(proyeccion.getEstadoActual()).isEqualTo("EN_ESPERA");
        assertThat(proyeccion.getActualizadaEn()).isEqualTo(T3);
        assertThat(proyeccion.getConfirmadaEn()).isEqualTo(T2);
        assertThat(proyeccion.getEnEsperaEn()).isEqualTo(T3);
    }

    @Test
    void aplicarEsTransaccional() throws NoSuchMethodException {
        assertThat(ProyeccionServiceImpl.class.getMethod("aplicar", EventoAtencion.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private ProyeccionAtencion proyeccionGuardada() {
        var captor = ArgumentCaptor.forClass(ProyeccionAtencion.class);
        verify(proyeccionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ProyeccionAtencion proyeccion(Long atencionId, String estado, Instant desde) {
        var proyeccion = new ProyeccionAtencion(atencionId, "Dental", estado, desde);
        proyeccion.registrarFechaDeEstado("SOLICITADA", T1);
        proyeccion.registrarFechaDeEstado(estado, desde);
        return proyeccion;
    }

    static EventoAtencion evento(String eventId, String type, Long atencionId, String estado,
                                 String estadoAnterior, Instant timestamp) {
        return new EventoAtencion(1, eventId, type, timestamp, "atencion-" + atencionId, "trace-1",
                new DatosEventoAtencion(atencionId, "Dental", "Box 3",
                        LocalDateTime.of(2026, 11, 20, 10, 30), estado, estadoAnterior,
                        new ActorEvento("oid-op", "Olga Operadora", "Operador")));
    }
}
