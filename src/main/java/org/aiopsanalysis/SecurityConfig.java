package org.aiopsanalysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // Permit all static resources
                .requestMatchers(new AntPathRequestMatcher("/index.html")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/shared/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/pages/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/vendor/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/dompurify/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll()
                // Permit direct page folder access (for legacy/direct links)
                .requestMatchers(new AntPathRequestMatcher("/alerts/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/applications/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/audit/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/connectors/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/dashboard/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/incidents/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/inventory/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/reports/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/schedules/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/settings/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/users/**")).permitAll()
                // Permit static file extensions
                .requestMatchers(new AntPathRequestMatcher("/*.html")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.css")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.js")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.png")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.jpg")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.svg")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.ico")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.woff")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.woff2")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.ttf")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/*.map")).permitAll()
                // Permit all other requests for now (SPA auth via headers)
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
