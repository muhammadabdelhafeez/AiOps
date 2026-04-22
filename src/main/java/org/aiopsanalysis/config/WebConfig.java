package org.aiopsanalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration for SPA support
 * Configures static resource handling and SPA routing
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static resources from /static/ folder
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);

        // Explicit mappings for new folder structure
        registry.addResourceHandler("/shared/**")
                .addResourceLocations("classpath:/static/shared/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/pages/**")
                .addResourceLocations("classpath:/static/pages/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/dompurify/**")
                .addResourceLocations("classpath:/static/dompurify/")
                .setCachePeriod(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root to index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
