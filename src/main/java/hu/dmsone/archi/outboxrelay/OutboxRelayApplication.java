package hu.dmsone.archi.outboxrelay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@EnableScheduling
public class OutboxRelayApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboxRelayApplication.class, args);
    }
}
