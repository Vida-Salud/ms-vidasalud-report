package cl.duoc.ms_vidasalud_report.dto;

import cl.duoc.ms_vidasalud_report.model.RangoReporte;

import java.time.Instant;
import java.util.List;

/**
 * KPIs de las atenciones creadas entre {@code desde} y {@code hasta}.
 * tiempoEsperaPromedioMinutos es null si en el rango ninguna atención pasó de EN_ESPERA a EN_ATENCION.
 */
public record KpisResponse(
        RangoReporte rango,
        Instant desde,
        Instant hasta,
        long totalAtenciones,
        Double tiempoEsperaPromedioMinutos,
        List<ConteoPorHora> atencionesPorHora,
        List<ConteoPorEstado> atencionesPorEstado) {
}
