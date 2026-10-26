package hu.dmsone.archi.platform.web.client;

import org.springframework.web.client.RestClient;

/**
 * RestClient gyár más szolgáltatások hívásához: a Spring Boot által konfigurált builderre épül, így a
 * {@code traceparent} automatikusan továbbmegy, és a felhasználó tokenje is.
 */
public class ServiceClients {

    private final RestClient.Builder builder;

    public ServiceClients(RestClient.Builder builder) {
        this.builder = builder;
    }

    public RestClient create(String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(new TokenRelayInterceptor())
                .build();
    }
}
