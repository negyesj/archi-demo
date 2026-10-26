package hu.dmsone.archi.iam.config;

import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class IamConfig {

    @Bean
    public KeycloakAdminClient keycloakAdminClient(RestClient.Builder builder, KeycloakProperties props) {
        return new KeycloakAdminClient(builder, props.url(), props.adminUser(), props.adminPassword());
    }
}
