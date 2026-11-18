package cl.duoc.ms_vidasalud_report.messaging.config;

import cl.duoc.ms_vidasalud_report.exception.EventoInvalidoException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo de errores del consumidor: reintentos y Dead Letter Topic.
 * <p>
 * Solo declara el DLT. {@code appointments.events} es de ms-vidasalud-appointments y lo declara él.
 */
@Configuration
public class KafkaConsumidorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumidorConfig.class);

    /** 3 reintentos (4 intentos en total) a 1 s, 2 s y 4 s antes de ir al DLT. */
    static final int REINTENTOS = 3;
    static final long INTERVALO_INICIAL_MS = 1000L;
    static final double MULTIPLICADOR = 2.0;
    static final long INTERVALO_MAXIMO_MS = 10_000L;

    @Bean
    public NewTopic topicoEventosDlt(@Value("${vidasalud.kafka.topico-dlt}") String topico) {
        return TopicBuilder.name(topico)
                // 3 particiones, igual que el original: el recoverer publica en la misma partición,
                // así que los mensajes fallidos de una atención quedan juntos y en orden.
                .partitions(3)
                // 3 réplicas: una copia en cada broker del clúster.
                .replicas(3)
                // Se borra por antigüedad: cada mensaje fallido se conserva tal cual para revisarlo,
                // no se compacta por key (se perderían fallas anteriores de la misma atención).
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                // 14 días, el doble que el tópico original: el DLT se revisa a mano y hay que dar
                // tiempo para diagnosticar y reprocesar antes de que se borre.
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                // Con acks=all, un mensaje enviado al DLT se confirma cuando está en al menos 2
                // réplicas: se tolera la caída de un broker sin perder mensajes fallidos.
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }

    /**
     * Template que usa el recoverer para escribir en el DLT. El valor puede ser de dos tipos:
     * <ul>
     *   <li>{@code byte[]}: el mensaje no se pudo deserializar y se reenvía tal cual llegó.</li>
     *   <li>{@code EventoAtencion} (u otro objeto): se deserializó pero falló el procesamiento; se
     *       publica como JSON, sin headers de tipo, igual que el productor original.</li>
     * </ul>
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplateDlt(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        Map<Class<?>, Serializer<?>> serializadores = new LinkedHashMap<>();
        serializadores.put(byte[].class, new ByteArraySerializer());
        serializadores.put(Object.class, new JacksonJsonSerializer<>().noTypeInfo());

        var productorFactory = new DefaultKafkaProducerFactory<String, Object>(config,
                new StringSerializer(), new DelegatingByTypeSerializer(serializadores, true));
        return new KafkaTemplate<>(productorFactory);
    }

    /**
     * Boot asigna este error handler al container factory del {@code @KafkaListener}.
     * <p>
     * Errores transitorios (p. ej. Oracle caído): 3 reintentos con espera creciente (1 s, 2 s, 4 s)
     * para no saturar al recurso que está fallando, y luego DLT. Cada intento fallido queda en el log
     * con tópico, partición, offset y key.
     * Errores permanentes: directo al DLT, reintentar daría el mismo resultado.
     * {@code DeserializationException} ya viene como no reintentable en la lista por defecto.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplateDlt,
                                            @Value("${vidasalud.kafka.topico-dlt}") String topicoDlt) {
        var dlpr = new DeadLetterPublishingRecoverer(kafkaTemplateDlt,
                (registro, error) -> new TopicPartition(topicoDlt, registro.partition()));

        ConsumerAwareRecordRecoverer recoverer = (registro, consumer, error) -> {
            log.error("Mensaje enviado al DLT [topic={}, partition={}, offset={}, key={}, dlt={}]: {}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(), topicoDlt,
                    error.getMessage(), error);
            dlpr.accept(registro, consumer, error);
        };

        var backOff = new ExponentialBackOffWithMaxRetries(REINTENTOS);
        backOff.setInitialInterval(INTERVALO_INICIAL_MS);
        backOff.setMultiplier(MULTIPLICADOR);
        backOff.setMaxInterval(INTERVALO_MAXIMO_MS);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setRetryListeners((registro, error, intento) ->
                log.warn("Falló el procesamiento, intento {} de {} [topic={}, partition={}, offset={}, key={}]: {}",
                        intento, REINTENTOS + 1, registro.topic(), registro.partition(), registro.offset(),
                        registro.key(), error.getMessage()));
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        return handler;
    }
}
