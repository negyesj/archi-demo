package hu.dmsone.archi.tenantadmin.config;

import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakConfig {

    @Bean
    public KeycloakAdminClient keycloakAdminClient(RestClient.Builder builder, AdminProperties props) {
        return new KeycloakAdminClient(builder, props.keycloak().url(),
                props.keycloak().adminUser(), props.keycloak().adminPassword());
    }
}
