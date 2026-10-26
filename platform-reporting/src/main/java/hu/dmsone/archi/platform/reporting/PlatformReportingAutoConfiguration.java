package hu.dmsone.archi.platform.reporting;

import com.mongodb.client.MongoClient;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.db.TenantDbAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = TenantDbAutoConfiguration.class)
@ConditionalOnClass(MongoClient.class)
@EnableConfigurationProperties(ReportingProperties.class)
public class PlatformReportingAutoConfiguration {

    @Bean
    @ConditionalOnBean(SecretProvider.class)
    public TenantMongoTemplates tenantMongoTemplates(ReportingProperties props, SecretProvider secrets) {
        return new TenantMongoTemplates(props, secrets);
    }
}
