package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.messaging.dto.DatosEventoAtencion;
import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;
import cl.duoc.ms_vidasalud_report.model.EventoProcesado;
import cl.duoc.ms_vidasalud_report.model.ProyeccionAtencion;
import cl.duoc.ms_vidasalud_report.repository.EventoProcesadoRepository;
import cl.duoc.ms_vidasalud_report.repository.ProyeccionAtencionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene REPORT_EVENTO y REPORT_ATENCION a partir de los eventos de appointments.
 * <p>
 * El orden por atención lo garantiza Kafka (la key es el atencionId), pero se toleran duplicados
 * (entrega al menos una vez) y eventos atrasados (p. ej. reprocesados desde el DLT).
 */
@Service
public class ProyeccionServiceImpl implements ProyeccionService {

    private static final Logger log = LoggerFactory.getLogger(ProyeccionServiceImpl.class);

    private final EventoProcesadoRepository eventoRepository;
    private final ProyeccionAtencionRepository proyeccionRepository;

    public ProyeccionServiceImpl(EventoProcesadoRepository eventoRepository,
                                 ProyeccionAtencionRepository proyeccionRepository) {
        this.eventoRepository = eventoRepository;
        this.proyeccionRepository = proyeccionRepository;
    }

    /**
     * Transaccional: el registro del evento y la proyección se guardan juntos. Si algo falla, no queda
     * el eventId marcado como procesado y el reintento lo vuelve a aplicar completo.
     */
    @Transactional
    @Override
    public void aplicar(EventoAtencion evento) {
        DatosEventoAtencion datos = evento.payload();
        if (eventoRepository.existsById(evento.eventId())) {
            log.info("Evento duplicado, se ignora [eventId={}, atencionId={}]",
                    evento.eventId(), datos.atencionId());
            return;
        }

        eventoRepository.save(new EventoProcesado(evento.eventId(), datos.atencionId(), evento.type(),
                datos.estado(), datos.estadoAnterior(), datos.servicio(), evento.timestamp()));

        ProyeccionAtencion proyeccion = proyeccionRepository.findById(datos.atencionId())
                .orElseGet(() -> new ProyeccionAtencion(datos.atencionId(), datos.servicio(),
                        datos.estado(), evento.timestamp()));

        if (!proyeccion.registrarFechaDeEstado(datos.estado(), evento.timestamp())) {
            log.warn("Estado desconocido, no se marca ninguna fecha [eventId={}, atencionId={}, estado={}]",
                    evento.eventId(), datos.atencionId(), datos.estado());
        }

        if (evento.timestamp().isBefore(proyeccion.getActualizadaEn())) {
            // Llegó tarde: ya se aplicó un cambio posterior, así que solo se completa su fecha.
            log.info("Evento anterior al estado actual, no lo reemplaza [eventId={}, atencionId={}, estado={}, estadoActual={}]",
                    evento.eventId(), datos.atencionId(), datos.estado(), proyeccion.getEstadoActual());
        } else {
            proyeccion.actualizarEstado(datos.estado(), datos.servicio(), evento.timestamp());
        }

        proyeccionRepository.save(proyeccion);
    }
}
