package cl.duoc.ms_vidasalud_report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de cada evento aplicado. Es la base de la idempotencia: Kafka entrega al menos una vez,
 * y un eventId que ya está aquí no se vuelve a aplicar.
 * <p>
 * Implementa {@link Persistable} para que {@code save} haga un INSERT directo (el id lo asigna el
 * productor): si un duplicado llegara a colarse, choca con la PK en vez de sobrescribir el registro.
 */
@Entity
@Table(name = "REPORT_EVENTO", indexes = @Index(name = "IX_REPORT_EVENTO_ATENCION", columnList = "ATENCION_ID"))
public class EventoProcesado implements Persistable<String> {

    @Id
    @Column(name = "EVENT_ID", length = 64)
    private String eventId;

    @Column(name = "ATENCION_ID", nullable = false)
    private Long atencionId;

    @Column(name = "TYPE", nullable = false, length = 40)
    private String type;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "ESTADO_ANTERIOR", length = 20)
    private String estadoAnterior;

    @Column(name = "SERVICIO", nullable = false, length = 100)
    private String servicio;

    @Column(name = "OCURRIDO_EN", nullable = false)
    private Instant ocurridoEn;   // timestamp del evento (UTC), no la hora en que se consumió

    @Transient
    private boolean nuevo = true;

    protected EventoProcesado() {
    }

    public EventoProcesado(String eventId, Long atencionId, String type, String estado,
                           String estadoAnterior, String servicio, Instant ocurridoEn) {
        this.eventId = eventId;
        this.atencionId = atencionId;
        this.type = type;
        this.estado = estado;
        this.estadoAnterior = estadoAnterior;
        this.servicio = servicio;
        this.ocurridoEn = ocurridoEn;
    }

    @PostLoad
    @PostPersist
    private void marcarExistente() {
        this.nuevo = false;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getAtencionId() {
        return atencionId;
    }

    public String getType() {
        return type;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getServicio() {
        return servicio;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    /** Igualdad por eventId, que es único por definición del contrato. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventoProcesado otro)) {
            return false;
        }
        return eventId != null && eventId.equals(otro.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "EventoProcesado{eventId='%s', atencionId=%d, type=%s, estado=%s, ocurridoEn=%s}"
                .formatted(eventId, atencionId, type, estado, ocurridoEn);
    }
}
