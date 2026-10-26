package hu.dmsone.archi.irat;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.web.idempotency.IdempotencyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IratApplication {

    public static void main(String[] args) {
        SpringApplication.run(IratApplication.class, args);
    }

    @Bean
    public IdempotencyService idempotencyService(DataSource dataSource, TransactionTemplate tx, ObjectMapper objectMapper) {
        return new IdempotencyService(dataSource, tx, objectMapper, "irat");
    }
}
