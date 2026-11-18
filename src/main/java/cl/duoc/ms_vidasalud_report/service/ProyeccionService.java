package cl.duoc.ms_vidasalud_report.service;

import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;

public interface ProyeccionService {

    /** Aplica un evento de atención a las proyecciones. Es idempotente por eventId. */
    void aplicar(EventoAtencion evento);
}
