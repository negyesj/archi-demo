package hu.dmsone.archi.provisioner;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.util.Passwords;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Tenant MongoDB riport adatbázis ({@code rpt_<kod>}) saját, csak ahhoz jogosult felhasználóval. */
@Component
public class MongoProvisioner {

    private static final Logger log = LoggerFactory.getLogger(MongoProvisioner.class);

    private final ProvisionerProperties props;
    private final SecretProvider secrets;

    public MongoProvisioner(ProvisionerProperties props, SecretProvider secrets) {
        this.props = props;
        this.secrets = secrets;
    }

    public void provision(String code) {
        String dbName = TenantNames.mongoDatabase(code);
        String user = TenantNames.mongoUser(code);
        String password = secrets.get(TenantNames.mongoSecretKey(code)).orElseGet(() -> Passwords.generate(24));
        try (MongoClient client = MongoClients.create(props.mongoAdminUri())) {
            MongoDatabase db = client.getDatabase(dbName);
            List<Document> roles = List.of(new Document("role", "readWrite").append("db", dbName),
                    new Document("role", "dbAdmin").append("db", dbName));
            try {
                db.runCommand(new Document("createUser", user).append("pwd", password).append("roles", roles));
            } catch (MongoCommandException e) {
                if (e.getErrorCode() == 51003) { // a felhasználó már létezik
                    db.runCommand(new Document("updateUser", user).append("pwd", password).append("roles", roles));
                } else {
                    throw e;
                }
            }
        }
        secrets.put(TenantNames.mongoSecretKey(code), password);
        log.info("MongoDB kész: {}", dbName);
    }
}
