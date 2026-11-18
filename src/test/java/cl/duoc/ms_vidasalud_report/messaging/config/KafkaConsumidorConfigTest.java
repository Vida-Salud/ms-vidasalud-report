package cl.duoc.ms_vidasalud_report.messaging.config;

import cl.duoc.ms_vidasalud_report.exception.EventoInvalidoException;
import cl.duoc.ms_vidasalud_report.messaging.dto.DatosEventoAtencion;
import cl.duoc.ms_vidasalud_report.messaging.dto.EventoAtencion;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumidorConfigTest {

    private static final String DLT = "appointments.events.report.DLT";

    private final KafkaConsumidorConfig config = new KafkaConsumidorConfig();

    @Test
    void dltConLaConfiguracionAcordada() {
        NewTopic topico = config.topicoEventosDlt(DLT);

        assertThat(topico.name()).isEqualTo(DLT);
        assertThat(topico.numPartitions()).isEqualTo(3);
        assertThat(topico.replicationFactor()).isEqualTo((short) 3);
        assertThat(topico.configs())
                .containsEntry("cleanup.policy", "delete")
                .containsEntry("retention.ms", "1209600000")
                .containsEntry("min.insync.replicas", "2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventosInvalidosYErroresDeDeserializacionNoSeReintentan() {
        DefaultErrorHandler handler = config.errorHandler(mock(KafkaTemplate.class), DLT);

        // removeClassification devuelve la clasificación que tenía: false = no reintentable.
        assertThat(handler.removeClassification(EventoInvalidoException.class)).isFalse();
        assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
        // Lo demás (p. ej. una caída de Oracle) no está clasificado y se reintenta.
        assertThat(handler.removeClassification(IllegalStateException.class)).isNull();
    }

    @Test
    void templateDelDltPublicaBytesTalCualYObjetosComoJson() {
        KafkaTemplate<String, Object> template = config.kafkaTemplateDlt(new KafkaProperties());
        Serializer<Object> serializer = ((DefaultKafkaProducerFactory<String, Object>) template
                .getProducerFactory()).getValueSerializer();

        byte[] crudo = "{no es json".getBytes(StandardCharsets.UTF_8);
        assertThat(serializer.serialize(DLT, new RecordHeaders(), crudo)).isEqualTo(crudo);

        var evento = new EventoAtencion(1, "ev-1", EventoAtencion.TYPE_CREADA,
                Instant.parse("2026-11-10T12:00:00Z"), null, null,
                new DatosEventoAtencion(7L, "Dental", null, null, "SOLICITADA", null, null));
        var headers = new RecordHeaders();
        String json = new String(serializer.serialize(DLT, headers, evento), StandardCharsets.UTF_8);
        assertThat(json).contains("\"eventId\":\"ev-1\"", "\"atencionId\":7");
        assertThat(headers.toArray()).as("sin headers de tipo").isEmpty();
    }
}
