package org.kfh.aiops.platform.tenant;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantWebMvcConfig implements WebMvcConfigurer {

    private final TenantContextResolver tenantContextResolver;

    public TenantWebMvcConfig(TenantContextResolver tenantContextResolver) {
        this.tenantContextResolver = tenantContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(tenantContextResolver);
    }
}

