package hu.dmsone.archi.platform.logging;

import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Központi, futás közben állítható loglevel. Minden példány saját consumer grouppal olvassa a compacted
 * {@code platform.loglevel} topicot, így minden példányra (Cloud Run, Kubernetes) érvényes lesz.
 */
public class LogLevelListener {

    private static final Logger log = LoggerFactory.getLogger(LogLevelListener.class);

    private final LoggingSystem loggingSystem;
    private final TenantLogTurboFilter turboFilter;
    private final ObjectMapper objectMapper;
    private final String serviceId;

    public LogLevelListener(LoggingSystem loggingSystem, TenantLogTurboFilter turboFilter,
                            ObjectMapper objectMapper, String serviceId) {
        this.loggingSystem = loggingSystem;
        this.turboFilter = turboFilter;
        this.objectMapper = objectMapper;
        this.serviceId = serviceId;
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            turboFilter.setContext(ctx);
            turboFilter.start();
            ctx.addTurboFilter(turboFilter);
        }
    }

    @KafkaListener(
            id = "#{'platform-loglevel-' + T(java.util.UUID).randomUUID().toString()}",
            topics = Topics.LOGLEVEL,
            groupId = "${spring.application.name}-loglevel-#{T(java.util.UUID).randomUUID().toString()}",
            properties = {"auto.offset.reset=earliest"})
    public void onMessage(ConsumerRecord<String, String> rec) {
        if (rec.value() == null) {
            return;
        }
        try {
            LogLevelMessage msg = objectMapper.readValue(rec.value(), LogLevelMessage.class);
            if (!"*".equals(msg.service()) && !serviceId.equals(msg.service())) {
                return;
            }
            String level = msg.expired() ? null : msg.level();
            String logger = msg.logger() == null ? LoggingSystem.ROOT_LOGGER_NAME : msg.logger();
            if (msg.tenant() != null && !msg.tenant().isBlank()) {
                turboFilter.set(msg.tenant(), logger, level);
            } else if (level == null && LoggingSystem.ROOT_LOGGER_NAME.equals(logger)) {
                loggingSystem.setLogLevel(logger, LogLevel.INFO);
            } else {
                loggingSystem.setLogLevel(logger, level == null ? null : LogLevel.valueOf(level));
            }
            log.info("Loglevel beállítva: logger={}, level={}, tenant={}", logger, level, msg.tenant());
        } catch (Exception e) {
            log.warn("Hibás loglevel üzenet: {}", rec.key(), e);
        }
    }
}
