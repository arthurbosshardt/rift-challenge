package com.riftrace.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftrace.account.dto.LinkRiotAccountRequest;
import com.riftrace.riot.RiotAccountClient;
import com.riftrace.riot.RiotSummonerClient;
import com.riftrace.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserRiotAccountServiceTest {

    @Mock
    private UserRiotAccountRepository userRiotAccountRepository;

    @Mock
    private RiotAccountClient riotAccountClient;

    @Mock
    private RiotSummonerClient riotSummonerClient;

    @InjectMocks
    private UserRiotAccountService userRiotAccountService;

    @Test
    void linkAccount_whenValid_persistsAccount() {
        UUID userId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154")).thenReturn(account);
        when(userRiotAccountRepository.existsByUserIdAndRiotPuuid(userId, "puuid-1")).thenReturn(false);
        when(userRiotAccountRepository.findByRiotPuuid("puuid-1")).thenReturn(Optional.empty());
        when(riotSummonerClient.findProfileIconId("puuid-1")).thenReturn(Optional.of(1234));
        when(userRiotAccountRepository.save(any(UserRiotAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userRiotAccountService.linkAccount(userId, new LinkRiotAccountRequest("Tanor#7154"));

        assertThat(response.riotId()).isEqualTo("Tanor#7154");
        assertThat(response.profileIconId()).isEqualTo(1234);
        verify(userRiotAccountRepository).save(any(UserRiotAccount.class));
    }

    @Test
    void linkAccount_whenLinkedToAnotherUser_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154")).thenReturn(account);
        when(userRiotAccountRepository.existsByUserIdAndRiotPuuid(userId, "puuid-1")).thenReturn(false);
        when(userRiotAccountRepository.findByRiotPuuid("puuid-1"))
                .thenReturn(Optional.of(UserRiotAccount.create(otherUserId, account)));

        assertThatThrownBy(() -> userRiotAccountService.linkAccount(userId, new LinkRiotAccountRequest("Tanor#7154")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("another user");

        verify(userRiotAccountRepository, never()).save(any());
    }

    @Test
    void findLinkedAccount_whenProfileIconMissing_fetchesAndPersistsIcon() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );

        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        when(riotSummonerClient.findProfileIconId("puuid-1")).thenReturn(Optional.of(5678));
        when(userRiotAccountRepository.save(account)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userRiotAccountService.findLinkedAccount(userId);

        assertThat(response).isPresent();
        assertThat(response.get().profileIconId()).isEqualTo(5678);
        assertThat(account.getProfileIconId()).isEqualTo(5678);
        verify(userRiotAccountRepository).save(account);
    }

    @Test
    void findLinkedAccount_whenRiotUnavailable_keepsAccountWithoutIcon() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );

        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        when(riotSummonerClient.findProfileIconId("puuid-1"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate limit"));

        var response = userRiotAccountService.findLinkedAccount(userId);

        assertThat(response).isPresent();
        assertThat(response.get().profileIconId()).isNull();
        verify(userRiotAccountRepository, never()).save(any());
    }

    @Test
    void listLinkedPuids_returnsStoredPuids() {
        UUID userId = UUID.randomUUID();
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                UserRiotAccount.create(userId, new RiotAccountDto("puuid-1", "Tanor", "7154"), 1)
        ));

        assertThat(userRiotAccountService.listLinkedPuids(userId)).containsExactly("puuid-1");
    }
}
