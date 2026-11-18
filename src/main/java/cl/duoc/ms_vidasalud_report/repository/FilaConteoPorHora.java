package cl.duoc.ms_vidasalud_report.repository;

/**
 * Fila de {@link ProyeccionAtencionRepository#contarPorHora}: la hora viene separada en campos
 * (UTC) porque así se agrupa igual en Oracle y en H2. El servicio la convierte en un Instant.
 */
public record FilaConteoPorHora(
        Integer anio,
        Integer mes,
        Integer dia,
        Integer hora,
        Long cantidad) {
}
