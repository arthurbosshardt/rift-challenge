package com.riftrace.account;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser findOrCreateFromSupabase(UUID supabaseId, String email, String preferredUsername) {
        return appUserRepository.findBySupabaseId(supabaseId)
                .orElseGet(() -> createFromSupabase(supabaseId, email, preferredUsername));
    }

    private AppUser createFromSupabase(UUID supabaseId, String email, String preferredUsername) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Supabase token missing email for first sign-in"
            );
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return appUserRepository.findByEmail(normalizedEmail)
                .map(existing -> linkSupabaseAccount(existing, supabaseId))
                .orElseGet(() -> appUserRepository.save(
                        AppUser.createFromSupabase(
                                supabaseId,
                                normalizedEmail,
                                resolveUsername(preferredUsername, normalizedEmail)
                        )
                ));
    }

    private AppUser linkSupabaseAccount(AppUser existing, UUID supabaseId) {
        existing.linkSupabaseId(supabaseId);
        return appUserRepository.save(existing);
    }

    private String resolveUsername(String preferredUsername, String email) {
        String candidate = sanitizeUsername(preferredUsername);
        if (candidate.isBlank()) {
            candidate = sanitizeUsername(email.substring(0, email.indexOf('@')));
        }
        return ensureUniqueUsername(candidate);
    }

    private String ensureUniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int suffix = 1;
        while (appUserRepository.existsByUsername(candidate)) {
            candidate = baseUsername + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String sanitizeUsername(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }
}
