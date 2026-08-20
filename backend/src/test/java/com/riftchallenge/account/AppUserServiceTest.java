package com.riftchallenge.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private AppUserService appUserService;

    @BeforeEach
    void setUp() {
        appUserService = new AppUserService(appUserRepository);
    }

    @Test
    void findOrCreateFromSupabase_whenEmailNotConfirmed_throwsUnauthorized() {
        UUID supabaseId = UUID.randomUUID();
        when(appUserRepository.findBySupabaseId(supabaseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.findOrCreateFromSupabase(
                supabaseId,
                "player@example.com",
                "player",
                false
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void findOrCreateFromSupabase_whenEmailAlreadyLinkedToOtherSupabase_throwsConflict() {
        UUID supabaseId = UUID.randomUUID();
        UUID otherSupabaseId = UUID.randomUUID();
        AppUser existing = AppUser.createFromSupabase(otherSupabaseId, "player@example.com", "player");
        when(appUserRepository.findBySupabaseId(supabaseId)).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("player@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> appUserService.findOrCreateFromSupabase(
                supabaseId,
                "player@example.com",
                "player",
                true
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void findOrCreateFromSupabase_linksExistingEmailWithoutSupabaseId() {
        UUID supabaseId = UUID.randomUUID();
        AppUser existing = AppUser.createFromSupabase(null, "player@example.com", "player");
        when(appUserRepository.findBySupabaseId(supabaseId)).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("player@example.com")).thenReturn(Optional.of(existing));
        when(appUserRepository.save(existing)).thenReturn(existing);

        AppUser result = appUserService.findOrCreateFromSupabase(
                supabaseId,
                "Player@Example.com",
                "player",
                true
        );

        assertThat(result.getSupabaseId()).isEqualTo(supabaseId);
        verify(appUserRepository).save(existing);
    }

    @Test
    void findOrCreateFromSupabase_createsNewUserWhenEmailUnknown() {
        UUID supabaseId = UUID.randomUUID();
        when(appUserRepository.findBySupabaseId(supabaseId)).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("player@example.com")).thenReturn(Optional.empty());
        when(appUserRepository.existsByUsername("player")).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser result = appUserService.findOrCreateFromSupabase(
                supabaseId,
                "player@example.com",
                "Player",
                true
        );

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getSupabaseId()).isEqualTo(supabaseId);
        assertThat(saved.getValue().getEmail()).isEqualTo("player@example.com");
        assertThat(result.getUsername()).isEqualTo("player");
    }
}
