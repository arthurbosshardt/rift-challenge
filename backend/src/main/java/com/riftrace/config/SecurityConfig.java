package com.riftrace.config;

import com.riftrace.authentication.SupabaseBearerAuthenticationFilter;
import com.riftrace.authentication.SupabaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SecurityConfig {

    private final SupabaseBearerAuthenticationFilter supabaseBearerAuthenticationFilter;

    public SecurityConfig(SupabaseBearerAuthenticationFilter supabaseBearerAuthenticationFilter) {
        this.supabaseBearerAuthenticationFilter = supabaseBearerAuthenticationFilter;
    }

    @Bean
    @Order(1)
    SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(
                        "/actuator/health",
                        "/api/races/public"
                )
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/races/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/races/share/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/races/*/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(supabaseBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}
