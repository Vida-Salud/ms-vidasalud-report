package cl.duoc.ms_vidasalud_report.config;

import cl.duoc.ms_vidasalud_report.model.RangoReporte;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convierte el query param {@code range} sin importar mayúsculas. Spring Boot registra los beans
 * {@link Converter} en Spring MVC; el conversor por defecto de enums exige el nombre exacto (LAST24H).
 * Un valor inválido termina en {@code MethodArgumentTypeMismatchException} (400 en ManejadorErrores).
 */
@Component
public class RangoReporteConverter implements Converter<String, RangoReporte> {

    @Override
    public RangoReporte convert(String texto) {
        return RangoReporte.desde(texto);
    }
}
