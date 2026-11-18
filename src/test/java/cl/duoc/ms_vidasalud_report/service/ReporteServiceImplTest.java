package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.dto.ConteoPorEstado;
import cl.duoc.ms_vidasalud_report.dto.ConteoPorHora;
import cl.duoc.ms_vidasalud_report.dto.ConteoPorServicio;
import cl.duoc.ms_vidasalud_report.dto.KpisResponse;
import cl.duoc.ms_vidasalud_report.dto.TopServiciosResponse;
import cl.duoc.ms_vidasalud_report.model.RangoReporte;
import cl.duoc.ms_vidasalud_report.repository.FilaConteoPorHora;
import cl.duoc.ms_vidasalud_report.repository.ProyeccionAtencionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-10T12:30:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Santiago"));

    @Mock ProyeccionAtencionRepository proyeccionRepository;
    ReporteService service;

    @BeforeEach
    void crearServicio() {
        service = new ReporteServiceImpl(proyeccionRepository, RELOJ);
    }

    @ParameterizedTest
    @CsvSource({
            "LAST24H, 2026-11-09T12:30:00Z",
            "LAST7D,  2026-11-03T12:30:00Z",
            "LAST30D, 2026-10-11T12:30:00Z"})
    void elRangoTerminaAhoraYEmpiezaSegunSuDuracion(RangoReporte rango, Instant desdeEsperado) {
        KpisResponse kpis = service.kpis(rango);

        assertThat(kpis.rango()).isEqualTo(rango);
        assertThat(kpis.hasta()).isEqualTo(AHORA);
        assertThat(kpis.desde()).isEqualTo(desdeEsperado);
        verify(proyeccionRepository).contarPorEstado(desdeEsperado, AHORA);
        verify(proyeccionRepository).contarPorHora(desdeEsperado, AHORA);
        verify(proyeccionRepository).promedioSegundosDeEspera(desdeEsperado, AHORA);
    }

    @Test
    void sinDatosDeEsperaElPromedioEsNull() {
        when(proyeccionRepository.promedioSegundosDeEspera(AHORA.minusSeconds(86400), AHORA)).thenReturn(null);

        KpisResponse kpis = service.kpis(RangoReporte.LAST24H);

        assertThat(kpis.tiempoEsperaPromedioMinutos()).isNull();
        assertThat(kpis.totalAtenciones()).isZero();
        assertThat(kpis.atencionesPorHora()).isEmpty();
        assertThat(kpis.atencionesPorEstado()).isEmpty();
    }

    @Test
    void kpisConviertenSegundosAMinutosYFilasAHoras() {
        Instant desde = AHORA.minus(RangoReporte.LAST24H.duracion());
        when(proyeccionRepository.promedioSegundosDeEspera(desde, AHORA)).thenReturn(754.0);
        when(proyeccionRepository.contarPorHora(desde, AHORA)).thenReturn(List.of(
                new FilaConteoPorHora(2026, 11, 10, 10, 1L),
                new FilaConteoPorHora(2026, 11, 10, 11, 2L)));
        when(proyeccionRepository.contarPorEstado(desde, AHORA)).thenReturn(List.of(
                new ConteoPorEstado("EN_ATENCION", 2L),
                new ConteoPorEstado("SOLICITADA", 1L)));

        KpisResponse kpis = service.kpis(RangoReporte.LAST24H);

        assertThat(kpis.tiempoEsperaPromedioMinutos()).isEqualTo(12.6);   // 754 s = 12,57 min
        assertThat(kpis.totalAtenciones()).isEqualTo(3);
        assertThat(kpis.atencionesPorHora()).containsExactly(
                new ConteoPorHora(Instant.parse("2026-11-10T10:00:00Z"), 1L),
                new ConteoPorHora(Instant.parse("2026-11-10T11:00:00Z"), 2L));
    }

    @Test
    void topServiciosUsaElRangoYElLimite() {
        Instant desde = AHORA.minus(RangoReporte.LAST7D.duracion());
        var dental = new ConteoPorServicio("Dental", 4L);
        when(proyeccionRepository.contarPorServicio(desde, AHORA, Limit.of(3))).thenReturn(List.of(dental));

        TopServiciosResponse top = service.topServicios(RangoReporte.LAST7D, 3);

        assertThat(top.rango()).isEqualTo(RangoReporte.LAST7D);
        assertThat(top.desde()).isEqualTo(desde);
        assertThat(top.hasta()).isEqualTo(AHORA);
        assertThat(top.servicios()).containsExactly(dental);
    }

    @Test
    void elRangoSeParseaSinImportarMayusculas() {
        assertThat(RangoReporte.desde("last24h")).isEqualTo(RangoReporte.LAST24H);
        assertThat(RangoReporte.desde(" Last7D ")).isEqualTo(RangoReporte.LAST7D);
        assertThat(RangoReporte.desde("LAST30D")).isEqualTo(RangoReporte.LAST30D);
        assertThatThrownBy(() -> RangoReporte.desde("last1y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last24h, last7d, last30d");
    }
}
