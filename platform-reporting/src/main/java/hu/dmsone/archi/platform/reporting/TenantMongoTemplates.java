package hu.dmsone.archi.platform.reporting;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantNames;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenantonként külön MongoDB adatbázis ({@code rpt_<tenant>}) a tenant saját felhasználójával.
 * Adatbázisnevet kézzel megadni nem lehet: mindig a tenant-kontextusból számolódik.
 */
public class TenantMongoTemplates implements DisposableBean {

    private record Entry(MongoClient client, MongoTemplate template) { }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final ReportingProperties props;
    private final SecretProvider secrets;

    public TenantMongoTemplates(ReportingProperties props, SecretProvider secrets) {
        this.props = props;
        this.secrets = secrets;
    }

    /** Az aktuális tenant riport adatbázisa. */
    public MongoTemplate current() {
        return forTenant(TenantContext.require());
    }

    public MongoTemplate forTenant(String tenant) {
        return entries.computeIfAbsent(tenant, this::create).template();
    }

    private Entry create(String tenant) {
        String db = TenantNames.mongoDatabase(tenant);
        String user = TenantNames.mongoUser(tenant);
        String password = secrets.get(TenantNames.mongoSecretKey(tenant))
                .orElseThrow(() -> new IllegalStateException("Nincs MongoDB titok a tenanthoz: " + tenant));
        String uri = "mongodb://" + enc(user) + ":" + enc(password) + "@" + props.getHost() + ":" + props.getPort()
                + "/" + db + "?authSource=" + db + (props.getOptions().isBlank() ? "" : "&" + props.getOptions());
        MongoClient client = MongoClients.create(uri);
        return new Entry(client, new MongoTemplate(client, db));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public void destroy() {
        entries.values().forEach(e -> e.client().close());
        entries.clear();
    }
}
