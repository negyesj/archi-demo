package hu.dmsone.archi.tenantadmin.config;

import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** A platform topicjainak létrehozása (a compacted topicok beállításaival). */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics platformTopics(AdminProperties props) {
        int r = props.kafka().replicas();
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.TENANTS).partitions(1).replicas(r).compact().build(),
                TopicBuilder.name(Topics.CATALOG).partitions(1).replicas(r).compact().build(),
                TopicBuilder.name(Topics.LOGLEVEL).partitions(1).replicas(r).compact().build(),
                TopicBuilder.name(Topics.TENANT_COMMANDS).partitions(3).replicas(r).build(),
                TopicBuilder.name(Topics.TENANT_EVENTS).partitions(3).replicas(r).build(),
                TopicBuilder.name(Topics.PLATFORM_AUDIT).partitions(3).replicas(r).build(),
                topic(Topics.IAM_EVENTS, r),
                topic(Topics.domain("partner"), r),
                topic(Topics.domain("irat"), r),
                topic(Topics.domain("webhook"), r));
    }

    private static NewTopic topic(String name, int replicas) {
        return TopicBuilder.name(name).partitions(6).replicas(replicas).config("retention.ms", "-1").build();
    }
}
