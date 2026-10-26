package hu.dmsone.archi.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.keycloak")
public record KeycloakProperties(String url, String adminUser, String adminPassword) { }
