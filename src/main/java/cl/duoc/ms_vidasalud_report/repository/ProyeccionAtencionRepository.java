package cl.duoc.ms_vidasalud_report.repository;

import cl.duoc.ms_vidasalud_report.dto.ConteoPorEstado;
import cl.duoc.ms_vidasalud_report.dto.ConteoPorServicio;
import cl.duoc.ms_vidasalud_report.model.ProyeccionAtencion;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Consultas de KPIs: todas agregan en la base de datos. "Atenciones del rango" son las que se
 * crearon entre {@code desde} y {@code hasta} (creadaEn).
 */
public interface ProyeccionAtencionRepository extends JpaRepository<ProyeccionAtencion, Long> {

    /** Atenciones creadas por hora (UTC). Solo aparecen las horas con al menos una atención. */
    @Query("""
            select new cl.duoc.ms_vidasalud_report.repository.FilaConteoPorHora(
                extract(year from p.creadaEn), extract(month from p.creadaEn),
                extract(day from p.creadaEn), extract(hour from p.creadaEn), count(p))
            from ProyeccionAtencion p
            where p.creadaEn between :desde and :hasta
            group by extract(year from p.creadaEn), extract(month from p.creadaEn),
                extract(day from p.creadaEn), extract(hour from p.creadaEn)
            order by extract(year from p.creadaEn), extract(month from p.creadaEn),
                extract(day from p.creadaEn), extract(hour from p.creadaEn)
            """)
    List<FilaConteoPorHora> contarPorHora(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /**
     * Promedio en segundos entre EN_ESPERA y EN_ATENCION, de las atenciones con ambas fechas dentro
     * del rango. Devuelve null si no hay ninguna.
     */
    @Query("""
            select avg((p.enAtencionEn - p.enEsperaEn) by second)
            from ProyeccionAtencion p
            where p.enEsperaEn between :desde and :hasta
              and p.enAtencionEn between :desde and :hasta
            """)
    Double promedioSegundosDeEspera(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("""
            select new cl.duoc.ms_vidasalud_report.dto.ConteoPorEstado(p.estadoActual, count(p))
            from ProyeccionAtencion p
            where p.creadaEn between :desde and :hasta
            group by p.estadoActual
            order by count(p) desc, p.estadoActual
            """)
    List<ConteoPorEstado> contarPorEstado(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** De mayor a menor; a igual cantidad, por nombre para que el resultado sea estable. */
    @Query("""
            select new cl.duoc.ms_vidasalud_report.dto.ConteoPorServicio(p.servicio, count(p))
            from ProyeccionAtencion p
            where p.creadaEn between :desde and :hasta
            group by p.servicio
            order by count(p) desc, p.servicio
            """)
    List<ConteoPorServicio> contarPorServicio(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                              Limit limite);
}
