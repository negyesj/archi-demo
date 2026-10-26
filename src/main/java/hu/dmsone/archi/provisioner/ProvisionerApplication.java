package hu.dmsone.archi.provisioner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Worker: nincs HTTP szervere, a {@code platform.tenant.commands} topicot dolgozza fel. */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
        MongoAutoConfiguration.class})
@ConfigurationPropertiesScan
public class ProvisionerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProvisionerApplication.class, args);
    }
}
