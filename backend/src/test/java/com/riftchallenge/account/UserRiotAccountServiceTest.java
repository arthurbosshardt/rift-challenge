package com.riftchallenge.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.dto.LinkRiotAccountRequest;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotSummonerClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
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
    void linkAccount_whenValid_persistsPrimaryAccount() {
        UUID userId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(0L);
        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.empty());
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154")).thenReturn(account);
        when(userRiotAccountRepository.existsByUserIdAndRiotPuuid(userId, "puuid-1")).thenReturn(false);
        when(userRiotAccountRepository.findByRiotPuuid("puuid-1")).thenReturn(Optional.empty());
        when(riotSummonerClient.findProfileIconId("puuid-1", com.riftchallenge.riot.ChallengeRegion.EUW)).thenReturn(Optional.of(1234));
        when(userRiotAccountRepository.save(any(UserRiotAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userRiotAccountService.linkAccount(userId, new LinkRiotAccountRequest("Tanor#7154"));

        assertThat(response.riotId()).isEqualTo("Tanor#7154");
        assertThat(response.primary()).isTrue();
        assertThat(response.profileIconId()).isEqualTo(1234);
        verify(userRiotAccountRepository).save(any(UserRiotAccount.class));
    }

    @Test
    void linkAccount_whenPrimaryExists_persistsSmurfAccount() {
        UUID userId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-2", "Smurf", "EUW");
        UserRiotAccount primary = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-1", "Tanor", "7154"),
                1,
                true
        );

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(1L);
        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.of(primary));
        when(riotAccountClient.getAccountByRiotId("Smurf", "EUW")).thenReturn(account);
        when(userRiotAccountRepository.existsByUserIdAndRiotPuuid(userId, "puuid-2")).thenReturn(false);
        when(userRiotAccountRepository.findByRiotPuuid("puuid-2")).thenReturn(Optional.empty());
        when(riotSummonerClient.findProfileIconId("puuid-2", com.riftchallenge.riot.ChallengeRegion.EUW)).thenReturn(Optional.of(4321));
        when(userRiotAccountRepository.save(any(UserRiotAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userRiotAccountService.linkAccount(
                userId,
                new LinkRiotAccountRequest("Smurf#EUW", true)
        );

        assertThat(response.primary()).isFalse();
        assertThat(response.riotId()).isEqualTo("Smurf#EUW");
    }

    @Test
    void linkAccount_whenPrimaryExistsWithoutSmurfFlag_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount primary = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-1", "Tanor", "7154"),
                1,
                true
        );

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(1L);
        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.of(primary));

        assertThatThrownBy(() -> userRiotAccountService.linkAccount(userId, new LinkRiotAccountRequest("Smurf#EUW")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("smurfs");

        verify(userRiotAccountRepository, never()).save(any());
    }

    @Test
    void linkAccount_whenLinkedToAnotherUser_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        when(userRiotAccountRepository.countByUserId(userId)).thenReturn(0L);
        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.empty());
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
                new RiotAccountDto("puuid-1", "Tanor", "7154"),
                null,
                true
        );

        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.of(account));
        when(riotSummonerClient.findProfileIconId("puuid-1", com.riftchallenge.riot.ChallengeRegion.EUW)).thenReturn(Optional.of(5678));
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
                new RiotAccountDto("puuid-1", "Tanor", "7154"),
                null,
                true
        );

        when(userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)).thenReturn(Optional.of(account));
        when(riotSummonerClient.findProfileIconId("puuid-1", com.riftchallenge.riot.ChallengeRegion.EUW))
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
                UserRiotAccount.create(userId, new RiotAccountDto("puuid-1", "Tanor", "7154"), 1, true),
                UserRiotAccount.create(userId, new RiotAccountDto("puuid-2", "Smurf", "EUW"), 2, false)
        ));

        assertThat(userRiotAccountService.listLinkedPuids(userId)).containsExactly("puuid-1", "puuid-2");
    }

    @Test
    void unlinkPrimaryAccount_promotesNextSmurf() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount primary = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-1", "Tanor", "7154"),
                1,
                true
        );
        UserRiotAccount smurf = UserRiotAccount.create(
                userId,
                new RiotAccountDto("puuid-2", "Smurf", "EUW"),
                2,
                false
        );

        when(userRiotAccountRepository.findByIdAndUserId(primary.getId(), userId)).thenReturn(Optional.of(primary));
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(primary, smurf));
        when(userRiotAccountRepository.save(smurf)).thenAnswer(invocation -> invocation.getArgument(0));

        userRiotAccountService.unlinkAccount(userId, primary.getId());

        assertThat(smurf.isPrimaryAccount()).isTrue();
        verify(userRiotAccountRepository).save(smurf);
        verify(userRiotAccountRepository).delete(primary);
    }
}
