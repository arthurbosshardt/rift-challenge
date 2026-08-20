package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.ChallengeListSnapshot;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.RefreshEligibility;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeSummaryCacheServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeSummaryCacheRepository summaryCacheRepository;

    @Mock
    private ChallengeListCacheStateRepository stateRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeProgressService progressService;

    @Mock
    private ChallengeDuoProgressService duoProgressService;

    private ObjectMapper objectMapper;
    private ChallengeSummaryCacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        cacheService = new ChallengeSummaryCacheService(
                challengeRepository,
                summaryCacheRepository,
                stateRepository,
                participantRepository,
                progressService,
                duoProgressService,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                COOLDOWN
        );
    }

    @Test
    void refreshAll_computesAndPersistsSnapshot() {
        Challenge challenge = Challenge.create(
                java.util.UUID.randomUUID(), "Active", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        when(challengeRepository.findAll()).thenReturn(List.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());
        when(progressService.buildPreviewProgress(challenge, List.of())).thenReturn(List.of());
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)).thenReturn(Optional.empty());

        ChallengeListSnapshot snapshot = cacheService.refreshAll(NOW);

        verify(summaryCacheRepository).deleteAllInBatch();
        ArgumentCaptor<List<ChallengeSummaryCache>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(summaryCacheRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(1);
        assertThat(rowsCaptor.getValue().getFirst().getChallengeId()).isEqualTo(challenge.getId());
        verify(stateRepository).save(any(ChallengeListCacheState.class));

        assertThat(snapshot.generatedAt()).isEqualTo(NOW);
        assertThat(snapshot.challenges()).hasSize(1);
        assertThat(snapshot.challenges().getFirst().name()).isEqualTo("Active");
    }

    @Test
    void readOrBootstrap_whenNoStateExists_computesAndPersists() {
        Challenge challenge = Challenge.create(
                java.util.UUID.randomUUID(), "Active", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)).thenReturn(Optional.empty());
        when(challengeRepository.findAll()).thenReturn(List.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());
        when(progressService.buildPreviewProgress(challenge, List.of())).thenReturn(List.of());

        ChallengeListSnapshot snapshot = cacheService.readOrBootstrap();

        verify(summaryCacheRepository).saveAll(any());
        assertThat(snapshot.challenges()).hasSize(1);
    }

    @Test
    void readOrBootstrap_whenStateExists_returnsCachedSnapshotWithoutRecomputing() throws Exception {
        Challenge challenge = Challenge.create(
                java.util.UUID.randomUUID(), "Cached", ChallengeType.SOLOQ, NOW.minusSeconds(60));
        ChallengeSummaryResponse cachedSummary = ChallengeSummaryResponse.from(
                challenge, NOW, 0, List.of(), List.of(), List.of(), false);
        String payload = objectMapper.writeValueAsString(cachedSummary);

        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID))
                .thenReturn(Optional.of(new ChallengeListCacheState(NOW.minusSeconds(30))));
        when(summaryCacheRepository.findAllByOrderByStartAtDesc()).thenReturn(
                List.of(new ChallengeSummaryCache(challenge.getId(), challenge.getStartAt(), payload, NOW.minusSeconds(30))));

        ChallengeListSnapshot snapshot = cacheService.readOrBootstrap();

        assertThat(snapshot.generatedAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(snapshot.challenges()).hasSize(1);
        assertThat(snapshot.challenges().getFirst().name()).isEqualTo("Cached");
        verify(challengeRepository, never()).findAll();
        verify(summaryCacheRepository, never()).saveAll(any());
    }

    @Test
    void enforceCooldown_whenWithinCooldown_throwsTooManyRequests() {
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID))
                .thenReturn(Optional.of(new ChallengeListCacheState(NOW.minusSeconds(10))));

        assertThatThrownBy(() -> cacheService.enforceCooldown())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void enforceCooldown_whenCooldownElapsed_doesNotThrow() {
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID))
                .thenReturn(Optional.of(new ChallengeListCacheState(NOW.minusSeconds(120))));

        cacheService.enforceCooldown();
    }

    @Test
    void enforceCooldown_whenNoStateYet_allowsRefresh() {
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)).thenReturn(Optional.empty());

        cacheService.enforceCooldown();
    }

    @Test
    void eligibility_whenNoStateYet_isAvailableWithNoNextTime() {
        when(stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)).thenReturn(Optional.empty());

        RefreshEligibility eligibility = cacheService.eligibility();

        assertThat(eligibility.refreshAvailable()).isTrue();
        assertThat(eligibility.nextRefreshAvailableAt()).isNull();
    }

    @Test
    void upsertOne_writesSingleRowWithoutTouchingGlobalState() {
        Challenge challenge = Challenge.create(
                java.util.UUID.randomUUID(), "New challenge", ChallengeType.SOLOQ, NOW.plusSeconds(3600));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());

        cacheService.upsertOne(challenge, NOW);

        ArgumentCaptor<ChallengeSummaryCache> rowCaptor = ArgumentCaptor.forClass(ChallengeSummaryCache.class);
        verify(summaryCacheRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getChallengeId()).isEqualTo(challenge.getId());
        verifyNoInteractions(stateRepository);
    }
}
