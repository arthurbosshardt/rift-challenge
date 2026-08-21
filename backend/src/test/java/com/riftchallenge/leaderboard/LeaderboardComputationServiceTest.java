package com.riftchallenge.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.leaderboard.dto.LeaderboardEntryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository.ParticipantMatchHistorySince;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshot.SnapshotType;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardComputationServiceTest {

    private static final Instant SEASON_START = Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID CHALLENGE_A = UUID.randomUUID();
    private static final UUID CHALLENGE_B = UUID.randomUUID();

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeParticipantMatchRepository matchRepository;

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private ChampionIconUrlService championIconUrlService;

    private LeaderboardComputationService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardComputationService(
                participantRepository,
                matchRepository,
                rankSnapshotRepository,
                new LeaderboardProperties(SEASON_START, "admin@example.com"),
                championIconUrlService
        );
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(championIconUrlService.buildApiPath(any())).thenReturn(null);
    }

    @Test
    void belowMinGamesThreshold_excludedFromWinRateOnly() {
        ChallengeParticipant belowThreshold = participant(CHALLENGE_A, "puuid-below", "Below", "EUW");
        when(participantRepository.findAll()).thenReturn(List.of(belowThreshold));
        boolean[] wins = new boolean[19];
        java.util.Arrays.fill(wins, true);
        when(matchRepository.findHistorySince(belowThreshold.getId(), SEASON_START))
                .thenReturn(history(belowThreshold, wins));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byWinRate()).isEmpty();
        assertThat(snapshot.season().byWinStreak()).hasSize(1);
        assertThat(snapshot.season().byWinStreak().get(0).winStreak()).isEqualTo(19);
        assertThat(snapshot.season().byLpGained()).hasSize(1);
    }

    @Test
    void samePlayerAcrossTwoChallenges_mergesMatchHistoryIndependentOfChallenge() {
        ChallengeParticipant runA = participant(CHALLENGE_A, "puuid-same", "Player", "EUW");
        ChallengeParticipant runB = participant(CHALLENGE_B, "puuid-same", "Player", "EUW");
        when(participantRepository.findAll()).thenReturn(List.of(runA, runB));

        when(matchRepository.findHistorySince(runA.getId(), SEASON_START))
                .thenReturn(history(
                        runA,
                        true, false, true, false, false, true, false, false, false, false,
                        true, false, false, false, true, false, false, false, false, true
                ));
        when(matchRepository.findHistorySince(runB.getId(), SEASON_START))
                .thenReturn(history(
                        runB,
                        true, true, true, true, false, true, true, true, false, true,
                        true, true, true, true, false, true, true, true, false, true
                ));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        // One player, one leaderboard row — their solo queue games count the same regardless of
        // which challenge(s) synced them: 20 games (6 wins) + 20 games (16 wins) = 40 games, 22 wins.
        assertThat(snapshot.season().byWinRate()).hasSize(1);
        LeaderboardEntryResponse entry = snapshot.season().byWinRate().get(0);
        assertThat(entry.puuid()).isEqualTo("puuid-same");
        assertThat(entry.gamesPlayed()).isEqualTo(40);
        assertThat(entry.winRate()).isCloseTo(0.55, org.assertj.core.data.Offset.offset(0.001));
        assertThat(entry.position()).isEqualTo(1);
        assertThat(entry.recentMatches()).isNotEmpty();
    }

    @Test
    void rollingSevenDayWindow_excludesOlderMatchesButSeasonKeepsThem() {
        ChallengeParticipant p = participant(CHALLENGE_A, "puuid-rolling", "Rolling", "EUW");
        when(participantRepository.findAll()).thenReturn(List.of(p));

        List<ParticipantMatchHistorySince> allSeasonMatches = new ArrayList<>();
        allSeasonMatches.addAll(historyAt(p, NOW.minusSeconds(30L * 24 * 3600), true, 25));
        allSeasonMatches.addAll(historyAt(p, NOW.minusSeconds(2L * 24 * 3600), false, 20));
        when(matchRepository.findHistorySince(p.getId(), SEASON_START)).thenReturn(allSeasonMatches);

        LeaderboardSnapshot snapshot = service.compute(NOW);

        LeaderboardEntryResponse seasonEntry = snapshot.season().byWinStreak().get(0);
        LeaderboardEntryResponse rollingEntry = snapshot.last7Days().byWinStreak().get(0);

        assertThat(seasonEntry.gamesPlayed()).isEqualTo(45);
        assertThat(seasonEntry.winStreak()).isEqualTo(25);
        assertThat(rollingEntry.gamesPlayed()).isEqualTo(20);
        assertThat(rollingEntry.wins()).isZero();
        assertThat(rollingEntry.winStreak()).isZero();
    }

    @Test
    void rankList_fallsBackToBaselineSnapshotWhenNoRefreshExists() {
        ChallengeParticipant p = participant(CHALLENGE_A, "puuid-rank", "Ranked", "EUW");
        when(participantRepository.findAll()).thenReturn(List.of(p));
        when(matchRepository.findHistorySince(p.getId(), SEASON_START)).thenReturn(List.of());
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(p.getId(), SnapshotType.REFRESH))
                .thenReturn(Optional.empty());
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(p.getId(), SnapshotType.BASELINE))
                .thenReturn(Optional.of(RankSnapshot.create(
                        p.getId(), NOW, SnapshotType.BASELINE, "RANKED_SOLO_5x5", "DIAMOND", "II", 40, 10, 5
                )));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byRank()).hasSize(1);
        LeaderboardEntryResponse entry = snapshot.season().byRank().get(0);
        assertThat(entry.tier()).isEqualTo("DIAMOND");
        assertThat(entry.rankDivision()).isEqualTo("II");
        assertThat(entry.leaguePoints()).isEqualTo(40);
        assertThat(snapshot.last7Days().byRank()).isEqualTo(snapshot.season().byRank());
    }

    private static ChallengeParticipant participant(UUID challengeId, String puuid, String gameName, String tagLine) {
        return ChallengeParticipant.create(challengeId, new RiotAccountDto(puuid, gameName, tagLine));
    }

    private static List<ParticipantMatchHistorySince> history(ChallengeParticipant participant, boolean... wins) {
        List<ParticipantMatchHistorySince> result = new ArrayList<>();
        Instant start = SEASON_START.plusSeconds(3600);
        for (int i = 0; i < wins.length; i++) {
            // Riot match IDs are globally unique, so tests must give each participation its own
            // to avoid a same-index game from a different challenge accidentally deduping away.
            result.add(new TestHistory(
                    "match-" + participant.getId() + "-" + i,
                    wins[i],
                    1,
                    participant.getChallengeId(),
                    start.plusSeconds(i * 3600L)
            ));
        }
        return result;
    }

    private static List<ParticipantMatchHistorySince> historyAt(
            ChallengeParticipant participant,
            Instant gameStart,
            boolean win,
            int count
    ) {
        List<ParticipantMatchHistorySince> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new TestHistory(
                    "match-at-" + gameStart + "-" + i,
                    win,
                    1,
                    participant.getChallengeId(),
                    gameStart.plusSeconds(i)
            ));
        }
        return result;
    }

    private record TestHistory(
            String getMatchId,
            boolean isWin,
            Integer getChampionId,
            UUID getChallengeId,
            Instant getGameStart
    ) implements ParticipantMatchHistorySince {
    }
}
