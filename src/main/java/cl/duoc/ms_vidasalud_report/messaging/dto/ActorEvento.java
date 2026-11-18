package cl.duoc.ms_vidasalud_report.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usuario que realizó la acción: id = claim oid de Azure AD, rol = Admin, Operador, Auditor o Cliente. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActorEvento(
        String id,
        String nombre,
        String rol) {
}
