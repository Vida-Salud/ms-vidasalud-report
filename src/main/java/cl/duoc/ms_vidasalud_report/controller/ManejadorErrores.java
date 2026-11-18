package cl.duoc.ms_vidasalud_report.controller;

import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Un solo formato de error (ProblemDetail, RFC 9457) para toda la API.
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE)}: el handler de Spring Boot (problemdetails.enabled) se registra
 * con orden 0 y, sin esto, respondería primero sin agregar "errores".
 * No hay un {@code @ExceptionHandler(Exception.class)}: atraparía el AccessDeniedException de
 * {@code @PreAuthorize} y un 403 saldría como 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ManejadorErrores {

    private static final String PARAMETROS_INVALIDOS = "La petición tiene parámetros inválidos";

    /**
     * Un query param que no se puede convertir (por ejemplo {@code range=last1y} o
     * {@code limit=abc}). Si el tipo es un enum, se informan los valores permitidos.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarTipoInvalido(MethodArgumentTypeMismatchException e) {
        Class<?> tipo = e.getRequiredType();
        String mensaje = tipo != null && tipo.isEnum()
                ? "Valor inválido. Valores permitidos: " + valoresPermitidos(tipo)
                : "Valor inválido";
        return conErrores(PARAMETROS_INVALIDOS, Map.of(e.getName(), mensaje));
    }

    /** Restricciones (@Min, @Max...) sobre los parámetros de un método del controlador. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail manejarValidacionDeParametros(HandlerMethodValidationException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors()
                .forEach(error -> errores.putIfAbsent(
                        nombreParametro(resultado.getMethodParameter()), error.getDefaultMessage())));
        return conErrores(PARAMETROS_INVALIDOS, errores);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return conErrores("La petición tiene campos inválidos", errores);
    }

    private ProblemDetail conErrores(String detalle, Map<String, String> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problema.setProperty("errores", errores);
        return problema;
    }

    /** El nombre que ve el cliente (el del query param), no el de la variable Java. */
    private String nombreParametro(MethodParameter parametro) {
        RequestParam requestParam = parametro.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        return parametro.getParameterName();
    }

    /** Usa toString(): RangoReporte lo devuelve en la forma de la API ("last24h"). */
    private String valoresPermitidos(Class<?> tipoEnum) {
        return Arrays.stream(tipoEnum.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }
}
