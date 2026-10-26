package hu.dmsone.archi.provisioner;

import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public KeycloakAdminClient keycloakAdminClient(RestClient.Builder builder, ProvisionerProperties props) {
        return new KeycloakAdminClient(builder, props.keycloakUrl(), props.keycloakAdminUser(), props.keycloakAdminPassword());
    }
}
