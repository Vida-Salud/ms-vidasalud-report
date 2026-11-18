package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.dto.ConteoPorEstado;
import cl.duoc.ms_vidasalud_report.dto.ConteoPorHora;
import cl.duoc.ms_vidasalud_report.dto.ConteoPorServicio;
import cl.duoc.ms_vidasalud_report.dto.KpisResponse;
import cl.duoc.ms_vidasalud_report.dto.TopServiciosResponse;
import cl.duoc.ms_vidasalud_report.model.RangoReporte;
import cl.duoc.ms_vidasalud_report.repository.FilaConteoPorHora;
import cl.duoc.ms_vidasalud_report.repository.ProyeccionAtencionRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Calcula los KPIs sobre REPORT_ATENCION para una ventana que termina ahora (según el Clock inyectado). */
@Service
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    private final ProyeccionAtencionRepository proyeccionRepository;
    private final Clock clock;

    public ReporteServiceImpl(ProyeccionAtencionRepository proyeccionRepository, Clock clock) {
        this.proyeccionRepository = proyeccionRepository;
        this.clock = clock;
    }

    @Override
    public KpisResponse kpis(RangoReporte rango) {
        Instant hasta = Instant.now(clock);
        Instant desde = hasta.minus(rango.duracion());

        List<ConteoPorEstado> porEstado = proyeccionRepository.contarPorEstado(desde, hasta);
        // Cada atención tiene exactamente un estado actual: el total es la suma de los grupos.
        long total = porEstado.stream().mapToLong(ConteoPorEstado::cantidad).sum();
        List<ConteoPorHora> porHora = proyeccionRepository.contarPorHora(desde, hasta).stream()
                .map(ReporteServiceImpl::aConteoPorHora)
                .toList();

        return new KpisResponse(rango, desde, hasta, total,
                aMinutos(proyeccionRepository.promedioSegundosDeEspera(desde, hasta)),
                porHora, porEstado);
    }

    @Override
    public TopServiciosResponse topServicios(RangoReporte rango, int limite) {
        Instant hasta = Instant.now(clock);
        Instant desde = hasta.minus(rango.duracion());

        List<ConteoPorServicio> servicios = proyeccionRepository.contarPorServicio(desde, hasta, Limit.of(limite));
        return new TopServiciosResponse(rango, desde, hasta, servicios);
    }

    private static ConteoPorHora aConteoPorHora(FilaConteoPorHora fila) {
        Instant hora = LocalDateTime.of(fila.anio(), fila.mes(), fila.dia(), fila.hora(), 0)
                .toInstant(ZoneOffset.UTC);
        return new ConteoPorHora(hora, fila.cantidad());
    }

    /** Segundos a minutos con un decimal; null si no hubo datos. */
    private static Double aMinutos(Double segundos) {
        if (segundos == null) {
            return null;
        }
        return Math.round(segundos / 60.0 * 10) / 10.0;
    }
}
