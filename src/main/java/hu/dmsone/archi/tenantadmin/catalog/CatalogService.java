package hu.dmsone.archi.tenantadmin.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.tenantadmin.config.AdminProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Szolgáltatás-katalógus: a szolgáltatások induláskor publikált manifestjei. Ebből ellenőrizzük a tenant
 * konfiguráció függőségeit ({@code dependsOn}): egy szolgáltatás csak a függőségeivel együtt kapcsolható be.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final AdminProperties props;

    public CatalogService(JdbcClient jdbc, ObjectMapper objectMapper, AdminProperties props) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public record CatalogEntry(String id, String name, String version, boolean core, JsonNode manifest) { }

    @KafkaListener(topics = Topics.CATALOG, groupId = "tenant-admin-catalog", properties = {"auto.offset.reset=earliest"})
    public void onManifest(ConsumerRecord<String, String> rec) throws Exception {
        if (rec.value() == null) {
            return;
        }
        JsonNode m = objectMapper.readTree(rec.value());
        if (m.path("platform").asBoolean(false)) {
            return;
        }
        jdbc.sql("""
                insert into catalog_service (id, name, version, manifest, updated_at)
                values (:id, :name, :version, cast(:manifest as jsonb), now())
                on conflict (id) do update set name = excluded.name, version = excluded.version,
                    manifest = excluded.manifest, updated_at = now()
                """)
                .param("id", m.path("id").asText())
                .param("name", m.path("name").asText(m.path("id").asText()))
                .param("version", m.path("version").asText("0"))
                .param("manifest", m.toString())
                .update();
        log.info("Katalógus frissítve: {} {}", m.path("id").asText(), m.path("version").asText());
    }

    public List<CatalogEntry> list() {
        List<CatalogEntry> list = new ArrayList<>();
        jdbc.sql("select id, name, version, manifest from catalog_service order by id").query((rs, i) -> {
            JsonNode manifest;
            try {
                manifest = objectMapper.readTree(rs.getString("manifest"));
            } catch (Exception e) {
                manifest = objectMapper.createObjectNode();
            }
            String id = rs.getString("id");
            return new CatalogEntry(id, rs.getString("name"), rs.getString("version"),
                    props.coreServices().contains(id), manifest);
        }).list().forEach(list::add);
        return list;
    }

    /** A bekapcsolandó szolgáltatások halmaza zárt-e a függőségekre nézve. */
    public void validate(Set<String> enabled) {
        Map<String, CatalogEntry> byId = list().stream().collect(Collectors.toMap(CatalogEntry::id, Function.identity()));
        Set<String> available = new HashSet<>(enabled);
        available.addAll(props.coreServices());
        List<String> errors = new ArrayList<>();
        for (String s : enabled) {
            CatalogEntry entry = byId.get(s);
            if (entry == null) {
                errors.add("Ismeretlen szolgáltatás: " + s);
                continue;
            }
            for (JsonNode dep : entry.manifest().path("dependsOn")) {
                String d = dep.asText();
                if (!"core".equals(d) && !available.contains(d)) {
                    errors.add(s + " szolgáltatás függ ettől: " + d);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(String.join("; ", errors));
        }
    }
}
