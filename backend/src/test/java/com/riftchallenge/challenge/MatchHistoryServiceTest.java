package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository.ParticipantMatchHistoryRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantMatchRepository participantMatchRepository;

    @Mock
    private ChampionIconUrlService championIconUrlService;

    @InjectMocks
    private MatchHistoryService matchHistoryService;

    @Test
    void buildForParticipant_ordersNewestFirstWithEstimatedLp() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Challenge",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-16T00:00:00Z"),
                Instant.parse("2026-08-18T00:00:00Z"),
                true
        );
        UUID challengeId = challenge.getId();
        when(championIconUrlService.buildApiPath(eq(103))).thenReturn("/api/champion-icons/103.png");
        when(championIconUrlService.buildApiPath(eq(86))).thenReturn("/api/champion-icons/86.png");
        ChallengeParticipant participant = participant(challengeId, "Tanor");
        ParticipantProgressResponse progress = ParticipantProgressResponse.withRankData(
                participant,
                1,
                "GOLD",
                "II",
                50,
                20,
                1000,
                2,
                1
        );

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantMatchRepository.findHistoryByParticipantIdAndChallengeId(participant.getId(), challengeId))
                .thenReturn(List.of(
                        row("m-new", true, 103, Instant.parse("2026-08-17T12:00:00Z")),
                        row("m-old", false, 86, Instant.parse("2026-08-16T12:00:00Z"))
                ));

        var history = matchHistoryService.buildForParticipant(participant, progress);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).win()).isTrue();
        assertThat(history.get(0).championId()).isEqualTo(103);
        assertThat(history.get(0).championIconUrl()).isEqualTo("/api/champion-icons/103.png");
        assertThat(history.get(0).lpDelta()).isEqualTo(20);
        assertThat(history.get(1).win()).isFalse();
        assertThat(history.get(1).lpDelta()).isEqualTo(-20);
    }

    @Test
    void buildForParticipant_excludesMatchesOutsideChallengeWindow() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Challenge",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-16T00:00:00Z"),
                Instant.parse("2026-08-18T00:00:00Z"),
                true
        );
        UUID challengeId = challenge.getId();
        ChallengeParticipant participant = participant(challengeId, "Tanor");
        ParticipantProgressResponse progress = ParticipantProgressResponse.withoutRankData(participant, 0, 0);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantMatchRepository.findHistoryByParticipantIdAndChallengeId(participant.getId(), challengeId))
                .thenReturn(List.of(
                        row("m-in", true, 103, Instant.parse("2026-08-17T12:00:00Z")),
                        row("m-before", false, 86, Instant.parse("2026-08-15T12:00:00Z")),
                        row("m-after", false, 64, Instant.parse("2026-08-18T12:00:00Z"))
                ));

        var history = matchHistoryService.buildForParticipant(participant, progress);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).playedAt()).isEqualTo(Instant.parse("2026-08-17T12:00:00Z"));
    }

    @Test
    void isWithinChallengeWindow_respectsStartAndEnd() {
        Instant start = Instant.parse("2026-08-16T00:00:00Z");
        Instant end = Instant.parse("2026-08-18T00:00:00Z");

        assertThat(MatchHistoryService.isWithinChallengeWindow(Instant.parse("2026-08-16T00:00:00Z"), start, end))
                .isTrue();
        assertThat(MatchHistoryService.isWithinChallengeWindow(Instant.parse("2026-08-17T12:00:00Z"), start, end))
                .isTrue();
        assertThat(MatchHistoryService.isWithinChallengeWindow(Instant.parse("2026-08-15T23:59:59Z"), start, end))
                .isFalse();
        assertThat(MatchHistoryService.isWithinChallengeWindow(Instant.parse("2026-08-18T00:00:00Z"), start, end))
                .isFalse();
        assertThat(MatchHistoryService.isWithinChallengeWindow(Instant.parse("2026-08-17T12:00:00Z"), start, null))
                .isTrue();
    }

    private static ChallengeParticipant participant(UUID challengeId, String name) {
        return ChallengeParticipant.create(
                challengeId,
                new com.riftchallenge.riot.dto.RiotAccountDto("puuid", name, "EUW")
        );
    }

    private static ParticipantMatchHistoryRow row(
            String matchId,
            boolean win,
            int championId,
            Instant gameStart
    ) {
        return new ParticipantMatchHistoryRow() {
            @Override
            public String getMatchId() {
                return matchId;
            }

            @Override
            public boolean isWin() {
                return win;
            }

            @Override
            public Integer getChampionId() {
                return championId;
            }

            @Override
            public Instant getGameStart() {
                return gameStart;
            }
        };
    }
}
