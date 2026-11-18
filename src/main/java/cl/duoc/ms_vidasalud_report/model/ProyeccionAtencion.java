package cl.duoc.ms_vidasalud_report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;

/**
 * Estado actual de una atención y el instante en que entró a cada estado, armado a partir de los
 * eventos. Es la tabla sobre la que se calculan los KPIs (tiempos de espera, tasa de cancelación...).
 * <p>
 * Todas las fechas son el timestamp del evento (UTC). {@code actualizadaEn} es el del último evento
 * que movió {@code estadoActual}: un evento más antiguo solo completa su fecha.
 */
@Entity
@Table(name = "REPORT_ATENCION")
public class ProyeccionAtencion implements Persistable<Long> {

    @Id
    @Column(name = "ATENCION_ID")
    private Long atencionId;

    @Column(name = "SERVICIO", nullable = false, length = 100)
    private String servicio;

    @Column(name = "ESTADO_ACTUAL", nullable = false, length = 20)
    private String estadoActual;

    @Column(name = "CREADA_EN")
    private Instant creadaEn;

    @Column(name = "CONFIRMADA_EN")
    private Instant confirmadaEn;

    @Column(name = "EN_ESPERA_EN")
    private Instant enEsperaEn;

    @Column(name = "EN_ATENCION_EN")
    private Instant enAtencionEn;

    @Column(name = "CERRADA_EN")
    private Instant cerradaEn;

    @Column(name = "CANCELADA_EN")
    private Instant canceladaEn;

    @Column(name = "ACTUALIZADA_EN", nullable = false)
    private Instant actualizadaEn;

    @Transient
    private boolean nueva = true;

    protected ProyeccionAtencion() {
    }

    public ProyeccionAtencion(Long atencionId, String servicio, String estadoActual, Instant actualizadaEn) {
        this.atencionId = atencionId;
        this.servicio = servicio;
        this.estadoActual = estadoActual;
        this.actualizadaEn = actualizadaEn;
    }

    @PostLoad
    @PostPersist
    private void marcarExistente() {
        this.nueva = false;
    }

    /**
     * Anota el instante en que la atención entró a {@code estado}. Si ya había una fecha se queda con
     * la menor, así el resultado no depende del orden en que lleguen los eventos.
     *
     * @return false si el estado no es uno de los conocidos (no hay columna que marcar)
     */
    public boolean registrarFechaDeEstado(String estado, Instant ocurridoEn) {
        switch (estado) {
            case "SOLICITADA" -> creadaEn = menor(creadaEn, ocurridoEn);
            case "CONFIRMADA" -> confirmadaEn = menor(confirmadaEn, ocurridoEn);
            case "EN_ESPERA" -> enEsperaEn = menor(enEsperaEn, ocurridoEn);
            case "EN_ATENCION" -> enAtencionEn = menor(enAtencionEn, ocurridoEn);
            case "CERRADA" -> cerradaEn = menor(cerradaEn, ocurridoEn);
            case "CANCELADA" -> canceladaEn = menor(canceladaEn, ocurridoEn);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Mueve el estado actual. Lo llama el servicio solo con eventos tan o más nuevos que actualizadaEn. */
    public void actualizarEstado(String estado, String servicio, Instant ocurridoEn) {
        this.estadoActual = estado;
        this.servicio = servicio;
        this.actualizadaEn = ocurridoEn;
    }

    private static Instant menor(Instant actual, Instant nuevo) {
        return actual == null || nuevo.isBefore(actual) ? nuevo : actual;
    }

    @Override
    public Long getId() {
        return atencionId;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    public Long getAtencionId() {
        return atencionId;
    }

    public String getServicio() {
        return servicio;
    }

    public String getEstadoActual() {
        return estadoActual;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getConfirmadaEn() {
        return confirmadaEn;
    }

    public Instant getEnEsperaEn() {
        return enEsperaEn;
    }

    public Instant getEnAtencionEn() {
        return enAtencionEn;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public Instant getCanceladaEn() {
        return canceladaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }

    /** Igualdad por atencionId: hay una sola proyección por atención. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProyeccionAtencion otra)) {
            return false;
        }
        return atencionId != null && atencionId.equals(otra.atencionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(atencionId);
    }

    @Override
    public String toString() {
        return "ProyeccionAtencion{atencionId=%d, servicio='%s', estadoActual=%s, actualizadaEn=%s}"
                .formatted(atencionId, servicio, estadoActual, actualizadaEn);
    }
}
