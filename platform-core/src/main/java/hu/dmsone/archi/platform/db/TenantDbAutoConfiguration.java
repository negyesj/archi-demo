package hu.dmsone.archi.platform.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import hu.dmsone.archi.platform.PlatformCoreAutoConfiguration;
import hu.dmsone.archi.platform.PlatformProperties;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.outbox.ActorProvider;
import hu.dmsone.archi.platform.outbox.OutboxWriter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Titoktár és tenant adatbázisok. A titoktár a {@code platform.platform-db.url} megadásakor,
 * a tenant-routing a {@code platform.tenant-db.enabled=true} esetén töltődik be.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class, after = PlatformCoreAutoConfiguration.class)
@ConditionalOnClass(name = {"com.zaxxer.hikari.HikariDataSource", "org.springframework.jdbc.core.JdbcTemplate"})
public class TenantDbAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.platform-db", name = "url")
    static class SecretsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SecretProvider secretProvider(PlatformProperties props) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(props.getPlatformDb().getUrl());
            ds.setUsername(props.getPlatformDb().getUsername());
            ds.setPassword(props.getPlatformDb().getPassword());
            ds.setMaximumPoolSize(2);
            ds.setMinimumIdle(0);
            ds.setPoolName("platform-secrets");
            return new JdbcSecretProvider(ds, props.getSecrets().getKey());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    @ConditionalOnProperty(prefix = "platform.tenant-db", name = "enabled", havingValue = "true")
    static class TenantRoutingConfiguration {

        @Bean
        public TenantDataSources tenantDataSources(PlatformProperties props, SecretProvider secrets) {
            return new TenantDataSources(props.getTenantDb(), secrets);
        }

        @Bean
        @Primary
        public DataSource tenantRoutingDataSource(TenantDataSources dataSources) {
            return new TenantRoutingDataSource(dataSources);
        }

        @Bean
        public TenantMigrator tenantMigrator(TenantDataSources dataSources, PlatformProperties props,
                                             ApplicationEventPublisher publisher) {
            return new TenantMigrator(dataSources, props.getTenantDb().getMigrations(), publisher);
        }

        @Bean
        @ConditionalOnMissingBean
        public OutboxWriter outboxWriter(DataSource tenantRoutingDataSource, ObjectMapper objectMapper,
                                         ActorProvider actorProvider, ObjectProvider<Tracer> tracer,
                                         PlatformProperties props) {
            String serviceId = props.getServiceId();
            String topic = props.getOutbox().getTopic() != null ? props.getOutbox().getTopic() : Topics.domain(serviceId);
            return new OutboxWriter(tenantRoutingDataSource, objectMapper, actorProvider, tracer, serviceId, topic);
        }
    }
}
