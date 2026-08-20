package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.AppUserRepository;
import com.riftchallenge.account.UserRiotAccountService;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.ChallengeListSnapshot;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.RefreshEligibility;
import com.riftchallenge.challenge.dto.CreateChallengeRequest;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import com.riftchallenge.challenge.dto.UpdateChallengeEndRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeScheduleRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeStartRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeNameRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeRequest;
import com.riftchallenge.synchronization.ChallengeSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeProgressService progressService;

    @Mock
    private ChallengeDuoProgressService duoProgressService;

    @Mock
    private ParticipantProfileService participantProfileService;

    @Mock
    private ChallengeRefreshRepository challengeRefreshRepository;

    @Mock
    private ChallengeSyncService challengeSyncService;

    @Mock
    private ChallengeSummaryCacheService summaryCacheService;

    @Mock
    private UserRiotAccountService userRiotAccountService;

    private ChallengeService challengeService;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(
                challengeRepository,
                appUserRepository,
                participantRepository,
                progressService,
                duoProgressService,
                participantProfileService,
                challengeRefreshRepository,
                challengeSyncService,
                summaryCacheService,
                userRiotAccountService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ChallengeSummaryResponse summaryOf(Challenge challenge) {
        return ChallengeSummaryResponse.from(challenge, NOW, 0, List.of(), List.of(), List.of(), false);
    }

    private static RefreshEligibility availableEligibility() {
        return new RefreshEligibility(true, null);
    }

    @Test
    void listPublicChallenges_withSearch_filtersByChallengeName() {
        Challenge matchingChallenge = Challenge.create(
                UUID.randomUUID(), "Les petits soldats", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        Challenge otherChallenge = Challenge.create(
                UUID.randomUUID(), "Autre challenge", ChallengeType.SOLOQ, NOW.minusSeconds(120));
        when(summaryCacheService.readOrBootstrap()).thenReturn(
                new ChallengeListSnapshot(List.of(summaryOf(matchingChallenge), summaryOf(otherChallenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        List<ChallengeSummaryResponse> challenges = challengeService.listPublicChallenges("soldats", null, null).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().name()).isEqualTo("Les petits soldats");
    }

    @Test
    void listPublicChallenges_withSearch_filtersByParticipant() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Duo challenge", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        when(summaryCacheService.readOrBootstrap()).thenReturn(
                new ChallengeListSnapshot(List.of(summaryOf(challenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());
        when(participantRepository.findDistinctPublicChallengeIdsByParticipantSearch(NOW, "tanor"))
                .thenReturn(List.of(challenge.getId()));

        List<ChallengeSummaryResponse> challenges = challengeService.listPublicChallenges(null, "tanor", null).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().name()).isEqualTo("Duo challenge");
    }

    @Test
    void listPublicChallenges_withShortSearch_ignoresSearchFilters() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Les petits soldats", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        when(summaryCacheService.readOrBootstrap()).thenReturn(
                new ChallengeListSnapshot(List.of(summaryOf(challenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        List<ChallengeSummaryResponse> challenges = challengeService.listPublicChallenges("so", "ta", null).challenges();

        assertThat(challenges).hasSize(1);
        verify(participantRepository, never()).findDistinctPublicChallengeIdsByParticipantSearch(any(), any());
    }

    @Test
    void listPublicChallenges_withTypeFilter_returnsOnlyMatchingType() {
        Challenge soloChallenge = Challenge.create(
                UUID.randomUUID(), "Solo challenge", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        Challenge duoChallenge = Challenge.create(
                UUID.randomUUID(), "Duo challenge", ChallengeType.DUOQ, NOW.minusSeconds(120));
        when(summaryCacheService.readOrBootstrap()).thenReturn(
                new ChallengeListSnapshot(List.of(summaryOf(soloChallenge), summaryOf(duoChallenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        List<ChallengeSummaryResponse> challenges =
                challengeService.listPublicChallenges(null, null, ChallengeType.DUOQ).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().type()).isEqualTo(ChallengeType.DUOQ);
    }

    @Test
    void listPublicChallenges_withChallengeNameAndSummoner_appliesBothFilters() {
        Challenge matchingChallenge = Challenge.create(
                UUID.randomUUID(), "Les petits soldats", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        Challenge otherChallenge = Challenge.create(
                UUID.randomUUID(), "Les petits soldats bis", ChallengeType.SOLOQ, NOW.minusSeconds(120));
        when(summaryCacheService.readOrBootstrap()).thenReturn(new ChallengeListSnapshot(
                List.of(summaryOf(matchingChallenge), summaryOf(otherChallenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());
        when(participantRepository.findDistinctPublicChallengeIdsByParticipantSearch(NOW, "tanor"))
                .thenReturn(List.of(matchingChallenge.getId()));

        List<ChallengeSummaryResponse> challenges =
                challengeService.listPublicChallenges("soldats", "tanor", null).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().id()).isEqualTo(matchingChallenge.getId());
    }

    @Test
    void listPublicChallenges_excludesChallengesNotYetStarted() {
        Challenge activeChallenge = Challenge.create(
                UUID.randomUUID(), "Active", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        Challenge notStartedChallenge = Challenge.create(
                UUID.randomUUID(), "Not started", ChallengeType.SOLOQ, NOW.plusSeconds(60));
        when(summaryCacheService.readOrBootstrap()).thenReturn(new ChallengeListSnapshot(
                List.of(summaryOf(activeChallenge), summaryOf(notStartedChallenge)), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        List<ChallengeSummaryResponse> challenges = challengeService.listPublicChallenges(null, null, null).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().name()).isEqualTo("Active");
    }

    @Test
    void listPublicChallenges_includesParticipantGameNamesForSoloChallenge() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Les petits soldats", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        ChallengeSummaryResponse summary = ChallengeSummaryResponse.from(
                challenge, NOW, 2, List.of("Tanor", "Kaori"), List.of(), List.of(), false);
        when(summaryCacheService.readOrBootstrap()).thenReturn(new ChallengeListSnapshot(List.of(summary), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        List<ChallengeSummaryResponse> challenges = challengeService.listPublicChallenges(null, null, null).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().entryCount()).isEqualTo(2);
        assertThat(challenges.getFirst().participantGameNames()).containsExactly("Tanor", "Kaori");
    }

    @Test
    void refreshPublicChallenges_recomputesAndReturnsFreshSnapshot() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Refreshed", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        ChallengeListSnapshot freshSnapshot = new ChallengeListSnapshot(List.of(summaryOf(challenge)), NOW);
        when(summaryCacheService.refreshAll(NOW)).thenReturn(freshSnapshot);
        when(summaryCacheService.eligibility()).thenReturn(new RefreshEligibility(false, NOW.plusSeconds(60)));

        var response = challengeService.refreshPublicChallenges();

        verify(summaryCacheService).enforceCooldown();
        assertThat(response.challenges()).hasSize(1);
        assertThat(response.generatedAt()).isEqualTo(NOW);
        assertThat(response.refreshAvailable()).isFalse();
    }

    @Test
    void createChallenge_rejectsEndBeforeStart() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);

        assertThatThrownBy(() -> challengeService.createChallenge(
                ownerId,
                new CreateChallengeRequest(
                        "Challenge",
                        ChallengeType.SOLOQ,
                        NOW,
                        NOW.minusSeconds(60),
                        null
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createChallenge_rejectsBothEndAtAndMaxGames() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);

        assertThatThrownBy(() -> challengeService.createChallenge(
                ownerId,
                new CreateChallengeRequest(
                        "Challenge",
                        ChallengeType.SOLOQ,
                        NOW,
                        NOW.plusSeconds(3600),
                        10
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createChallenge_rejectsMissingEndAtAndMaxGames() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);

        assertThatThrownBy(() -> challengeService.createChallenge(
                ownerId,
                new CreateChallengeRequest(
                        "Challenge",
                        ChallengeType.SOLOQ,
                        NOW,
                        null,
                        null
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createChallenge_rejectsDuplicateName() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);
        when(challengeRepository.existsByNameIgnoreCase("Existing Challenge")).thenReturn(true);

        assertThatThrownBy(() -> challengeService.createChallenge(
                ownerId,
                new CreateChallengeRequest(
                        "Existing Challenge",
                        ChallengeType.SOLOQ,
                        NOW,
                        NOW.plusSeconds(3600),
                        null
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void createChallenge_whenValid_upsertsCacheEntry() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildPreviewProgress(any(), any())).thenReturn(List.of());
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.createChallenge(
                ownerId,
                new CreateChallengeRequest("New challenge", ChallengeType.SOLOQ, NOW, NOW.plusSeconds(3600), null)
        );

        assertThat(response.name()).isEqualTo("New challenge");
        verify(summaryCacheService).upsertOne(any(Challenge.class), org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void updateSchedule_whenOwner_updatesBothDatesWithSingleRefresh() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Instant newEnd = NOW.plusSeconds(86_400);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateSchedule(
                challengeId,
                ownerId,
                new UpdateChallengeScheduleRequest(newStart, newEnd)
        );

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.endAt()).isEqualTo(newEnd);
    }

    @Test
    void updateSchedule_whenOwnerWithParticipants_doesNotAutoRefresh() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Instant newEnd = NOW.plusSeconds(86_400);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateSchedule(
                challengeId,
                ownerId,
                new UpdateChallengeScheduleRequest(newStart, newEnd)
        );

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.endAt()).isEqualTo(newEnd);
    }

    @Test
    void updateName_whenOwner_updatesName() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Old name", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateName(challengeId, ownerId, new UpdateChallengeNameRequest("New name"));

        assertThat(challenge.getName()).isEqualTo("New name");
        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.shareSlug()).isEqualTo(challengeId.toString());
        verify(challengeRepository).save(challenge);
    }

    @Test
    void updateChallenge_whenOwner_updatesNameAndSchedule() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Instant newEnd = NOW.plusSeconds(86_400);
        Challenge challenge = Challenge.create(
                ownerId, "Old name", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateChallenge(
                challengeId,
                ownerId,
                new UpdateChallengeRequest("New name", newStart, newEnd, null)
        );

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.endAt()).isEqualTo(newEnd);
        verify(challengeRepository).save(challenge);
        verify(challengeSyncService, never()).refreshChallenge(challengeId);
    }

    @Test
    void updateName_rejectsDuplicateName() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Old name", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.existsByNameIgnoreCaseAndIdNot("Taken name", challengeId)).thenReturn(true);

        assertThatThrownBy(() -> challengeService.updateName(challengeId, ownerId, new UpdateChallengeNameRequest("Taken name")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        verify(challengeRepository, never()).save(challenge);
    }

    @Test
    void deleteChallenge_whenOwner_deletesChallenge() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "To delete", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        challengeService.deleteChallenge(challengeId, ownerId);

        verify(challengeRepository).delete(challenge);
    }

    @Test
    void deleteChallenge_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.deleteChallenge(challengeId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);

        verify(challengeRepository, never()).delete(challenge);
    }

    @Test
    void updateStartAt_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.updateStartAt(
                challengeId,
                UUID.randomUUID(),
                new UpdateChallengeStartRequest(NOW.plusSeconds(1800))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void updateStartAt_whenChallengeAlreadyStarted_skipsRefreshWithoutParticipants() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateStartAt(challengeId, ownerId, new UpdateChallengeStartRequest(newStart));

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateStartAt_whenOwnerBeforeStart_updatesStart() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.plusSeconds(7200);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.plusSeconds(3600), NOW.plusSeconds(86_400));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateStartAt(challengeId, ownerId, new UpdateChallengeStartRequest(newStart));

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("NOT_STARTED");
    }

    @Test
    void updateStartAt_whenMovedToPast_skipsRefreshWithoutParticipants() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(60);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.plusSeconds(3600), NOW.plusSeconds(86_400));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateStartAt(challengeId, ownerId, new UpdateChallengeStartRequest(newStart));

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateEndAt_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeService.updateEndAt(
                challengeId,
                UUID.randomUUID(),
                new UpdateChallengeEndRequest(NOW.plusSeconds(7200))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void updateEndAt_whenOwner_updatesEnd() {
        UUID ownerId = UUID.randomUUID();
        Instant newEnd = NOW.plusSeconds(86_400);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(any())).thenReturn(Optional.empty());

        var response = challengeService.updateEndAt(challengeId, ownerId, new UpdateChallengeEndRequest(newEnd));

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.endAt()).isEqualTo(newEnd);
        assertThat(response.isOwner()).isTrue();
    }

    @Test
    void updateEndAt_whenRefreshOnCooldown_skipsSync() {
        UUID ownerId = UUID.randomUUID();
        Instant newEnd = NOW.plusSeconds(86_400);
        Challenge challenge = Challenge.create(
                ownerId, "Test", ChallengeType.SOLOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        UUID challengeId = challenge.getId();
        ChallengeRefresh recentRefresh = ChallengeRefresh.create(challengeId, NOW.minusSeconds(30));

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge)).thenReturn(challenge);
        when(challengeRefreshRepository.findByChallengeId(challengeId)).thenReturn(Optional.of(recentRefresh));

        var response = challengeService.updateEndAt(challengeId, ownerId, new UpdateChallengeEndRequest(newEnd));

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
        assertThat(response.endAt()).isEqualTo(newEnd);
        assertThat(response.refreshAvailable()).isFalse();
    }

    @Test
    void listParticipatingChallenges_returnsChallengesForLinkedAccounts() {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = Challenge.create(userId, "Joined", ChallengeType.DUOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600));

        when(userRiotAccountService.listLinkedPuids(userId)).thenReturn(List.of("puuid-1"));
        when(summaryCacheService.readOrBootstrap()).thenReturn(new ChallengeListSnapshot(List.of(), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());
        when(participantRepository.findDistinctChallengeIdsByRiotPuuidIn(List.of("puuid-1"))).thenReturn(List.of(challengeId));
        when(summaryCacheService.readByChallengeIds(List.of(challengeId))).thenReturn(List.of(summaryOf(challenge)));

        List<ChallengeSummaryResponse> challenges = challengeService.listParticipatingChallenges(userId).challenges();

        assertThat(challenges).hasSize(1);
        assertThat(challenges.getFirst().name()).isEqualTo("Joined");
    }

    @Test
    void listParticipatingChallenges_withoutLinkedAccounts_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userRiotAccountService.listLinkedPuids(userId)).thenReturn(List.of());
        when(summaryCacheService.readOrBootstrap()).thenReturn(new ChallengeListSnapshot(List.of(), NOW));
        when(summaryCacheService.eligibility()).thenReturn(availableEligibility());

        assertThat(challengeService.listParticipatingChallenges(userId).challenges()).isEmpty();
    }

    @Test
    void getByShareSlug_returnsDetail() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Some race", ChallengeType.SOLOQ, NOW.plusSeconds(3600));
        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());
        when(progressService.buildProgress(challenge, List.of())).thenReturn(List.of());
        when(challengeRefreshRepository.findByChallengeId(challenge.getId())).thenReturn(Optional.empty());

        var response = challengeService.getByShareSlug(challenge.getShareSlug(), ownerId);

        assertThat(response.name()).isEqualTo("Some race");
        assertThat(response.isOwner()).isTrue();
        assertThat(response.shareSlug()).isEqualTo(challenge.getId().toString());
    }

    @Test
    void getByShareSlug_anonymousCallerCanAccessAnyChallenge() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Some race", ChallengeType.SOLOQ, NOW.plusSeconds(3600));
        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());
        when(progressService.buildProgress(challenge, List.of())).thenReturn(List.of());
        when(challengeRefreshRepository.findByChallengeId(challenge.getId())).thenReturn(Optional.empty());

        var response = challengeService.getByShareSlug(challenge.getShareSlug(), null);

        assertThat(response.name()).isEqualTo("Some race");
        assertThat(response.isOwner()).isFalse();
    }

    @Test
    void refreshChallenge_whenChallengeMissing_throwsNotFound() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.existsById(challengeId)).thenReturn(false);

        assertThatThrownBy(() -> challengeService.refreshChallenge(challengeId, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);

        verify(challengeSyncService, never()).refreshChallenge(challengeId);
    }
}
