package com.riftchallenge.authentication;

import com.riftchallenge.account.AppUser;
import com.riftchallenge.account.AppUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SupabaseBearerAuthenticationFilter extends OncePerRequestFilter {

    private final SupabaseAuthClient supabaseAuthClient;
    private final AppUserService appUserService;

    public SupabaseBearerAuthenticationFilter(
            SupabaseAuthClient supabaseAuthClient,
            AppUserService appUserService
    ) {
        this.supabaseAuthClient = supabaseAuthClient;
        this.appUserService = appUserService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String accessToken = resolveBearerToken(request);
            if (accessToken != null) {
                supabaseAuthClient.fetchUser(accessToken).ifPresent(supabaseUser -> {
                    try {
                        AppUser appUser = appUserService.findOrCreateFromSupabase(
                                supabaseUser.id(),
                                supabaseUser.email(),
                                extractUsername(supabaseUser.userMetadata()),
                                supabaseUser.hasConfirmedEmail()
                        );
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(appUser.getId(), null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (ResponseStatusException ignored) {
                        // Invalid/unconfirmed first sign-in: leave request unauthenticated.
                    }
                });
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String extractUsername(Map<String, Object> userMetadata) {
        if (userMetadata == null) {
            return null;
        }
        for (String key : List.of("username", "preferred_username", "full_name", "name")) {
            Object value = userMetadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
