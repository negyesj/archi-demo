package hu.dmsone.archi.platform.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.InputStream;

/**
 * Induláskor a szolgáltatás {@code platform-manifest.json} leírását a compacted {@code platform.catalog}
 * topicra teszi. A tenant admin ebből építi a szolgáltatás-katalógust, az IAM a jogosultsági elemeket.
 */
public class ServiceManifestPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServiceManifestPublisher.class);
    public static final String RESOURCE = "platform-manifest.json";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public ServiceManifestPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publish() {
        ClassPathResource res = new ClassPathResource(RESOURCE);
        if (!res.exists()) {
            return;
        }
        try (InputStream in = res.getInputStream()) {
            JsonNode manifest = objectMapper.readTree(in);
            String id = manifest.path("id").asText();
            kafka.send(Topics.CATALOG, id, objectMapper.writeValueAsString(manifest));
            log.info("Szolgáltatás manifest publikálva: {} {}", id, manifest.path("version").asText());
        } catch (Exception e) {
            log.error("Manifest publikálás sikertelen", e);
        }
    }
}
