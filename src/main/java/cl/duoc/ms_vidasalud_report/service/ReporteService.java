package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.dto.KpisResponse;
import cl.duoc.ms_vidasalud_report.dto.TopServiciosResponse;
import cl.duoc.ms_vidasalud_report.model.RangoReporte;

public interface ReporteService {

    KpisResponse kpis(RangoReporte rango);

    TopServiciosResponse topServicios(RangoReporte rango, int limite);
}
