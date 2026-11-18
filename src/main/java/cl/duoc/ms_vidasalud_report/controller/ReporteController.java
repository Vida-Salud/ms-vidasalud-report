package cl.duoc.ms_vidasalud_report.controller;

import cl.duoc.ms_vidasalud_report.dto.KpisResponse;
import cl.duoc.ms_vidasalud_report.dto.TopServiciosResponse;
import cl.duoc.ms_vidasalud_report.model.RangoReporte;
import cl.duoc.ms_vidasalud_report.service.ReporteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPIs de atenciones. {@code range} acepta last24h, last7d o last30d (sin importar mayúsculas).
 * Las restricciones de {@code limit} las valida Spring MVC (HandlerMethodValidationException).
 * Solo el rol Admin ve la reportería (la misma regla que aplica el BFF en /api/report/**).
 */
@RestController
@RequestMapping("/api/{version}/report")
@PreAuthorize("hasAuthority('APPROLE_Admin')")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/kpis")
    public ResponseEntity<KpisResponse> kpis(
            @RequestParam(name = "range", defaultValue = "last24h") RangoReporte rango) {
        return ResponseEntity.ok(reporteService.kpis(rango));
    }

    @GetMapping("/top-services")
    public ResponseEntity<TopServiciosResponse> topServicios(
            @RequestParam(name = "range", defaultValue = "last7d") RangoReporte rango,
            @RequestParam(name = "limit", defaultValue = "5")
            @Min(value = 1, message = "debe estar entre 1 y 20")
            @Max(value = 20, message = "debe estar entre 1 y 20") int limit) {
        return ResponseEntity.ok(reporteService.topServicios(rango, limit));
    }
}
