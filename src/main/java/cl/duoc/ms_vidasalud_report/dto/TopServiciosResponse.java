package cl.duoc.ms_vidasalud_report.dto;

import cl.duoc.ms_vidasalud_report.model.RangoReporte;

import java.time.Instant;
import java.util.List;

/** Servicios con más atenciones creadas en el rango, de mayor a menor. */
public record TopServiciosResponse(
        RangoReporte rango,
        Instant desde,
        Instant hasta,
        List<ConteoPorServicio> servicios) {
}
