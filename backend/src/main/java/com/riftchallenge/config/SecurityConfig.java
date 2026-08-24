package com.riftchallenge.config;

import com.riftchallenge.authentication.SupabaseBearerAuthenticationFilter;
import com.riftchallenge.authentication.SupabaseProperties;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class SecurityConfig {

    private static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

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
                        "/api/health",
                        "/api/challenges/public",
                        "/api/leaderboard",
                        "/api/leaderboard/players/*/matches/*",
                        "/api/champion-icons/**",
                        "/api/challenge-preview",
                        "/api/challenge-preview-image",
                        "/api/sitemap.xml",
                        "/api/challenges/share/**"
                )
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(SecurityConfig::applySecurityHeaders)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(supabaseBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain authenticatedSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(SecurityConfig::applySecurityHeaders)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenges/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenges/share/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/leaderboard/players/*/matches/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/summoners/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/players/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenge-preview").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenge-preview-image").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sitemap.xml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenges/*/participants/*/matches/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/challenges/*/duos/*/matches/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/champion-icons/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/challenges/*/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/challenges/public/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(supabaseBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    private static void applySecurityHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers
    ) {
        headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31_536_000)
                )
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                ))
                .contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY));
    }
}
