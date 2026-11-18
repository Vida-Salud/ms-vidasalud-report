package cl.duoc.ms_vidasalud_report.exception;

/**
 * El evento llegó bien formado como JSON pero no cumple el contrato (faltan datos obligatorios).
 * No se reintenta: reprocesarlo daría el mismo resultado, así que va directo al DLT.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
