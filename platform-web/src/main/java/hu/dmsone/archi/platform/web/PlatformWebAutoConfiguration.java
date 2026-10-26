package hu.dmsone.archi.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.PlatformCoreAutoConfiguration;
import hu.dmsone.archi.platform.outbox.ActorProvider;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.web.client.ServiceClients;
import hu.dmsone.archi.platform.web.error.PlatformExceptionHandler;
import hu.dmsone.archi.platform.web.permission.PermissionAspect;
import hu.dmsone.archi.platform.web.permission.PermissionService;
import hu.dmsone.archi.platform.web.permission.PermissionSource;
import hu.dmsone.archi.platform.web.permission.RemoteIamPermissionSource;
import hu.dmsone.archi.platform.web.security.KeycloakJwtAuthenticationManagerResolver;
import hu.dmsone.archi.platform.web.security.SecurityActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/** Servlet szolgáltatások közös rétege. */
@AutoConfiguration(before = {PlatformCoreAutoConfiguration.class, SecurityAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PlatformWebProperties.class)
public class PlatformWebAutoConfiguration {

    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> keycloakAuthenticationManagerResolver(PlatformWebProperties props) {
        return new KeycloakJwtAuthenticationManagerResolver(props.getIssuerBase(), props.getJwksBase());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain platformSecurityFilterChain(HttpSecurity http,
                                                           AuthenticationManagerResolver<HttpServletRequest> resolver)
            throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                                "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(resolver));
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<TenantRequestFilter> tenantRequestFilter(
            TenantRegistry registry, ObjectMapper objectMapper, PlatformWebProperties props,
            @Value("${platform.service-id:${spring.application.name}}") String serviceId) {
        FilterRegistrationBean<TenantRequestFilter> reg =
                new FilterRegistrationBean<>(new TenantRequestFilter(registry, objectMapper, serviceId, props.isCoreService()));
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        reg.addUrlPatterns("/api/*", "/public/*");
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(PermissionSource.class)
    public PermissionSource remoteIamPermissionSource(RestClient.Builder builder, PlatformWebProperties props) {
        return new RemoteIamPermissionSource(builder, props.getIamBaseUrl());
    }

    @Bean
    public PermissionService permissionService(PermissionSource source, StringRedisTemplate redis,
                                               ObjectMapper objectMapper, PlatformWebProperties props) {
        return new PermissionService(source, redis, objectMapper, props.getPermissionCacheTtl());
    }

    @Bean
    public PermissionAspect permissionAspect(PermissionService permissionService) {
        return new PermissionAspect(permissionService);
    }

    @Bean
    @Primary
    public ActorProvider securityActorProvider(PermissionService permissionService,
                                               @Value("${platform.service-id:${spring.application.name}}") String serviceId) {
        return new SecurityActorProvider(permissionService, serviceId);
    }

    @Bean
    public PlatformExceptionHandler platformExceptionHandler() {
        return new PlatformExceptionHandler();
    }

    @Bean
    public ServiceClients serviceClients(RestClient.Builder builder) {
        return new ServiceClients(builder);
    }
}
