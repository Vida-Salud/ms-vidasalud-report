package cl.duoc.ms_vidasalud_report.dto;

import java.time.Instant;

/** Atenciones creadas en la hora que empieza en {@code hora} (UTC). */
public record ConteoPorHora(
        Instant hora,
        Long cantidad) {
}
