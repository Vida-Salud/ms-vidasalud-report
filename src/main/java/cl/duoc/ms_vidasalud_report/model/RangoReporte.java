package cl.duoc.ms_vidasalud_report.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Ventana de tiempo de un reporte, hacia atrás desde ahora. En la API se escribe en minúsculas
 * ("last24h"), pero se acepta sin importar mayúsculas.
 */
public enum RangoReporte {
    LAST24H(Duration.ofHours(24)),
    LAST7D(Duration.ofDays(7)),
    LAST30D(Duration.ofDays(30));

    private final Duration duracion;

    RangoReporte(Duration duracion) {
        this.duracion = duracion;
    }

    public Duration duracion() {
        return duracion;
    }

    /** @throws IllegalArgumentException si el texto no es un rango conocido */
    public static RangoReporte desde(String texto) {
        String normalizado = texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(r -> r.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rango inválido: '%s'. Valores permitidos: %s".formatted(texto, permitidos())));
    }

    public static String permitidos() {
        return Arrays.stream(values()).map(RangoReporte::toString).collect(Collectors.joining(", "));
    }

    /** Forma usada en la API (query param y JSON): "last24h". */
    @JsonValue
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
