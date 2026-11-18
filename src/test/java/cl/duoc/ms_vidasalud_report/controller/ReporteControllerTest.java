package cl.duoc.ms_vidasalud_report.controller;

import cl.duoc.ms_vidasalud_report.model.ProyeccionAtencion;
import cl.duoc.ms_vidasalud_report.repository.ProyeccionAtencionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los endpoints de KPIs sobre H2, con un reloj fijo para que los rangos sean deterministas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReporteControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-10T12:30:00Z");

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneId.of("America/Santiago"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ProyeccionAtencionRepository proyeccionRepository;

    @BeforeEach
    void guardarProyecciones() {
        proyeccionRepository.deleteAll();
        proyeccionRepository.saveAll(List.of(
                // Últimas 24 h: esperó 20 min y está en atención.
                atencion(1L, "Dental", Map.of(
                        "SOLICITADA", "2026-11-10T11:05:00Z",
                        "EN_ESPERA", "2026-11-10T11:10:00Z",
                        "EN_ATENCION", "2026-11-10T11:30:00Z")),
                // Últimas 24 h: esperó 10 min y ya se cerró.
                atencion(2L, "Dental", Map.of(
                        "SOLICITADA", "2026-11-10T11:20:00Z",
                        "EN_ESPERA", "2026-11-10T11:40:00Z",
                        "EN_ATENCION", "2026-11-10T11:50:00Z",
                        "CERRADA", "2026-11-10T12:00:00Z")),
                // Últimas 24 h: recién solicitada.
                atencion(3L, "Kinesiología", Map.of(
                        "SOLICITADA", "2026-11-10T10:15:00Z")),
                // Creada hace 30 h: fuera de las 24 h. Entró a espera antes del rango, así que
                // tampoco cuenta para el tiempo de espera aunque la atención empezó dentro.
                atencion(4L, "Dental", Map.of(
                        "SOLICITADA", "2026-11-09T06:30:00Z",
                        "EN_ESPERA", "2026-11-09T11:30:00Z",
                        "EN_ATENCION", "2026-11-09T13:30:00Z")),
                // Hace 3 días: solo entra en los últimos 7 días.
                atencion(5L, "Dental", Map.of(
                        "SOLICITADA", "2026-11-07T12:30:00Z",
                        "CANCELADA", "2026-11-07T13:30:00Z")),
                // Hace 40 días: fuera de todos los rangos.
                atencion(6L, "Pediatría", Map.of(
                        "SOLICITADA", "2026-10-01T12:30:00Z"))));
    }

    @Test
    void kpisPorDefectoSonDeLasUltimas24Horas() throws Exception {
        mockMvc.perform(get("/api/v1/report/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rango").value("last24h"))
                .andExpect(jsonPath("$.desde").value("2026-11-09T12:30:00Z"))
                .andExpect(jsonPath("$.hasta").value("2026-11-10T12:30:00Z"))
                .andExpect(jsonPath("$.totalAtenciones").value(3))
                .andExpect(jsonPath("$.tiempoEsperaPromedioMinutos").value(15.0))
                .andExpect(jsonPath("$.atencionesPorHora", hasSize(2)))
                .andExpect(jsonPath("$.atencionesPorHora[0].hora").value("2026-11-10T10:00:00Z"))
                .andExpect(jsonPath("$.atencionesPorHora[0].cantidad").value(1))
                .andExpect(jsonPath("$.atencionesPorHora[1].hora").value("2026-11-10T11:00:00Z"))
                .andExpect(jsonPath("$.atencionesPorHora[1].cantidad").value(2))
                .andExpect(jsonPath("$.atencionesPorEstado", hasSize(3)))
                .andExpect(jsonPath("$.atencionesPorEstado[?(@.estado == 'EN_ATENCION')].cantidad").value(1))
                .andExpect(jsonPath("$.atencionesPorEstado[?(@.estado == 'CERRADA')].cantidad").value(1))
                .andExpect(jsonPath("$.atencionesPorEstado[?(@.estado == 'SOLICITADA')].cantidad").value(1));
    }

    @Test
    void kpisDeLosUltimos7DiasSinImportarMayusculas() throws Exception {
        mockMvc.perform(get("/api/v1/report/kpis").param("range", "Last7D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rango").value("last7d"))
                .andExpect(jsonPath("$.totalAtenciones").value(5))
                // Ahora la atención 4 tiene ambas fechas dentro: (20 + 10 + 120) / 3 = 50 min.
                .andExpect(jsonPath("$.tiempoEsperaPromedioMinutos").value(50.0));
    }

    @Test
    void sinDatosEnElRangoElTiempoDeEsperaEsNull() throws Exception {
        proyeccionRepository.deleteAll();

        mockMvc.perform(get("/api/v1/report/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAtenciones").value(0))
                .andExpect(jsonPath("$.tiempoEsperaPromedioMinutos").doesNotExist())
                .andExpect(jsonPath("$.atencionesPorHora", hasSize(0)));
    }

    @Test
    void topServiciosPorDefectoSonDeLosUltimos7Dias() throws Exception {
        mockMvc.perform(get("/api/v1/report/top-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rango").value("last7d"))
                .andExpect(jsonPath("$.desde").value("2026-11-03T12:30:00Z"))
                .andExpect(jsonPath("$.servicios", hasSize(2)))
                .andExpect(jsonPath("$.servicios[0].servicio").value("Dental"))
                .andExpect(jsonPath("$.servicios[0].cantidad").value(4))
                .andExpect(jsonPath("$.servicios[1].servicio").value("Kinesiología"))
                .andExpect(jsonPath("$.servicios[1].cantidad").value(1));
    }

    @Test
    void topServiciosRespetaElLimite() throws Exception {
        mockMvc.perform(get("/api/v1/report/top-services").param("range", "last30d").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios", hasSize(1)))
                .andExpect(jsonPath("$.servicios[0].servicio").value("Dental"));
    }

    @Test
    void rangoInvalidoResponde400ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/report/kpis").param("range", "last1y"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores.range", containsString("last24h, last7d, last30d")));
    }

    @Test
    void limiteFueraDeRangoResponde400ConProblemDetail() throws Exception {
        for (String limite : new String[] {"0", "21"}) {
            mockMvc.perform(get("/api/v1/report/top-services").param("limit", limite))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errores.limit").value("debe estar entre 1 y 20"));
        }
    }

    @Test
    void limiteNoNumericoResponde400() throws Exception {
        mockMvc.perform(get("/api/v1/report/top-services").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.limit").value("Valor inválido"));
    }

    @Test
    void versionNoSoportadaResponde400() throws Exception {
        mockMvc.perform(get("/api/v9/report/kpis"))
                .andExpect(status().isBadRequest());
    }

    /** Proyección con la fecha de cada estado; el estado actual es el de la fecha más reciente. */
    private static ProyeccionAtencion atencion(Long id, String servicio, Map<String, String> fechas) {
        var ultima = fechas.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        var proyeccion = new ProyeccionAtencion(id, servicio, ultima.getKey(), Instant.parse(ultima.getValue()));
        fechas.forEach((estado, fecha) -> proyeccion.registrarFechaDeEstado(estado, Instant.parse(fecha)));
        return proyeccion;
    }
}
