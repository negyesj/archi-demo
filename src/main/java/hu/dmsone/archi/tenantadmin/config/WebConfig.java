package hu.dmsone.archi.tenantadmin.config;

import hu.dmsone.archi.tenantadmin.security.AdminGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminGuard adminGuard;

    public WebConfig(AdminGuard adminGuard) {
        this.adminGuard = adminGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminGuard).addPathPatterns("/api/admin/**");
    }
}
