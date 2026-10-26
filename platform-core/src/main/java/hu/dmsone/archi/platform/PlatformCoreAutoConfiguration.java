package hu.dmsone.archi.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.catalog.ServiceManifestPublisher;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.logging.LogLevelListener;
import hu.dmsone.archi.platform.logging.TenantLogTurboFilter;
import hu.dmsone.archi.platform.outbox.ActorProvider;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** A platform-core automatikus konfigurációja: minden szolgáltatásban (servlet és reaktív) betöltődik. */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, KafkaAutoConfiguration.class})
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformCoreAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.tenant-registry", name = "enabled", matchIfMissing = true)
    public TenantRegistry tenantRegistry(ObjectMapper objectMapper, ApplicationEventPublisher publisher) {
        return new TenantRegistry(objectMapper, publisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.logging", name = "dynamic", matchIfMissing = true)
    public TenantLogTurboFilter tenantLogTurboFilter() {
        return new TenantLogTurboFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.logging", name = "dynamic", matchIfMissing = true)
    public LogLevelListener logLevelListener(LoggingSystem loggingSystem, TenantLogTurboFilter filter,
                                             ObjectMapper objectMapper,
                                             @Value("${platform.service-id:${spring.application.name}}") String serviceId) {
        return new LogLevelListener(loggingSystem, filter, objectMapper, serviceId);
    }

    @Bean
    public ServiceManifestPublisher serviceManifestPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                             ObjectMapper objectMapper) {
        return new ServiceManifestPublisher(kafkaTemplate, objectMapper);
    }

    /**
     * Kafka feldolgozási hiba: 5 újrapróbálás 2 másodpercenként, utána a {@code <topic>.dlt} dead-letter topicra
     * kerül az üzenet (onnan a support újraküldheti).
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler platformKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (rec, ex) -> new TopicPartition(rec.topic() + ".dlt", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 5));
    }

    @Bean
    @ConditionalOnMissingBean
    public ActorProvider systemActorProvider(@Value("${platform.service-id:${spring.application.name}}") String serviceId) {
        return () -> EventEnvelope.Actor.system(serviceId);
    }
}
