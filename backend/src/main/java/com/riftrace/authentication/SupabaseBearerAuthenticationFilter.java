package com.riftrace.authentication;

import com.riftrace.account.AppUser;
import com.riftrace.account.AppUserService;
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
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String accessToken = resolveBearerToken(request);
            if (accessToken != null) {
                supabaseAuthClient.fetchUser(accessToken).ifPresent(supabaseUser -> {
                    AppUser appUser = appUserService.findOrCreateFromSupabase(
                            supabaseUser.id(),
                            supabaseUser.email(),
                            extractUsername(supabaseUser.userMetadata())
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(appUser.getId(), null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
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
        Object username = userMetadata.get("username");
        return username == null ? null : username.toString();
    }
}
