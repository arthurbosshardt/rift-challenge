package com.riftchallenge.challenge;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.challenge.dto.ChampionStatResponse;
import com.riftchallenge.challenge.dto.PlaystyleResponse;
import com.riftchallenge.challenge.dto.RecentGameResponse;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository.ChampionRankRow;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository.SeasonActivityRow;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchDurations;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecentActivityService {

    private static final Logger log = LoggerFactory.getLogger(RecentActivityService.class);
    private static final int CHAMPION_RANK_MIN_GAMES = 3;

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final RiotAccountRepository riotAccountRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RiotAccountService riotAccountService;
    private final LeaderboardAccountMatchRepository accountMatchRepository;
    private final ActivityAccountBackgroundSyncService backgroundSyncService;
    private final RiotLeagueClient riotLeagueClient;
    private final ChampionIconUrlService championIconUrlService;
    private final LeaderboardProperties leaderboardProperties;

    public RecentActivityService(
            UserRiotAccountRepository userRiotAccountRepository,
            RiotAccountRepository riotAccountRepository,
            ChallengeParticipantRepository participantRepository,
            RiotAccountService riotAccountService,
            LeaderboardAccountMatchRepository accountMatchRepository,
            ActivityAccountBackgroundSyncService backgroundSyncService,
            RiotLeagueClient riotLeagueClient,
            ChampionIconUrlService championIconUrlService,
            LeaderboardProperties leaderboardProperties
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.riotAccountRepository = riotAccountRepository;
        this.participantRepository = participantRepository;
        this.riotAccountService = riotAccountService;
        this.accountMatchRepository = accountMatchRepository;
        this.backgroundSyncService = backgroundSyncService;
        this.riotLeagueClient = riotLeagueClient;
        this.championIconUrlService = championIconUrlService;
        this.leaderboardProperties = leaderboardProperties;
    }

    public List<AccountRecentGamesResponse> listRecentGames(UUID userId) {
        Optional<UserRiotAccount> account = userRiotAccountRepository.findByUserId(userId);
        return account.map(link -> buildResponse(link.getRiotAccount(), link.getId())).map(List::of).orElseGet(List::of);
    }

    /**
     * Public lookup for the shareable player profile page — DB-only resolve, no ownership check.
     * Falls back to {@link ChallengeParticipantRepository} and registers the account into
     * {@link RiotAccount} on the fly, so this doesn't depend on {@code /players/{riotId}}
     * having been called first to register it.
     */
    public Optional<AccountRecentGamesResponse> getActivityForRiotId(String gameName, String tagLine) {
        Optional<RiotAccount> account = riotAccountRepository
                .findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase(gameName, tagLine);
        if (account.isEmpty()) {
            account = participantRepository
                    .findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc(gameName, tagLine)
                    .map(participant -> riotAccountService.findOrCreate(
                            new RiotAccountDto(participant.getRiotPuuid(), participant.getRiotGameName(), participant.getRiotTagLine()),
                            participant.getProfileIconId()
                    ));
        }
        return account.map(a -> buildResponse(a, a.getId()));
    }

    private AccountRecentGamesResponse buildResponse(RiotAccount account, UUID responseId) {
        Optional<RiotLeagueEntryDto> currentRank = fetchCurrentRank(account);
        int seasonMatchTotal = seasonMatchTotal(currentRank);

        List<SeasonActivityRow> seasonRows = accountMatchRepository.findSeasonActivitySince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt()
        );

        backgroundSyncService.scheduleSyncIfIdle(account, seasonMatchTotal, seasonRows.size());

        int syncedGames = seasonRows.size();
        boolean seasonSyncComplete = syncedGames >= seasonMatchTotal
                || account.isActivitySeasonHistoryExhausted()
                || backgroundSyncService.isSeasonHistoryExhausted(account.getId());
        boolean seasonSyncInProgress = !seasonSyncComplete;
        ChampionStatsResult statsResult = !seasonRows.isEmpty()
                ? buildSeasonChampionStatsFromRows(account.getRiotPuuid(), seasonRows)
                : ChampionStatsResult.EMPTY;

        return new AccountRecentGamesResponse(
                responseId,
                account.getRiotGameName(),
                account.getRiotTagLine(),
                account.getProfileIconId(),
                currentRank.map(RiotLeagueEntryDto::tier).orElse(null),
                currentRank.map(RiotLeagueEntryDto::rank).orElse(null),
                currentRank.map(RiotLeagueEntryDto::leaguePoints).orElse(null),
                seasonWins(currentRank, seasonRows),
                seasonLosses(currentRank, seasonRows),
                buildRecentGamesFromDatabase(account, seasonRows),
                statsResult.champions(),
                statsResult.playstyle(),
                seasonRows.size(),
                seasonSyncComplete ? syncedGames : seasonMatchTotal,
                seasonSyncComplete,
                seasonSyncInProgress
        );
    }

    private static int seasonMatchTotal(Optional<RiotLeagueEntryDto> currentRank) {
        return ActivitySeasonMatchTotals.seasonMatchTotal(currentRank);
    }

    private static Integer seasonWins(Optional<RiotLeagueEntryDto> currentRank, List<SeasonActivityRow> seasonRows) {
        if (currentRank.isPresent()) {
            return currentRank.get().wins();
        }
        if (seasonRows.isEmpty()) {
            return null;
        }
        return countWins(seasonRows);
    }

    private static Integer seasonLosses(Optional<RiotLeagueEntryDto> currentRank, List<SeasonActivityRow> seasonRows) {
        if (currentRank.isPresent()) {
            return currentRank.get().losses();
        }
        if (seasonRows.isEmpty()) {
            return null;
        }
        int wins = countWins(seasonRows);
        return seasonRows.size() - wins;
    }

    private static int countWins(List<SeasonActivityRow> seasonRows) {
        int wins = 0;
        for (SeasonActivityRow row : seasonRows) {
            if (row.isWin()) {
                wins++;
            }
        }
        return wins;
    }

    private Optional<RiotLeagueEntryDto> fetchCurrentRank(RiotAccount account) {
        try {
            // Linked accounts aren't region-tagged yet (unlike challenges); assumes EUW.
            return riotLeagueClient.findRankedSoloEntry(account.getRiotPuuid(), ChallengeRegion.EUW);
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Unable to fetch current rank for account {}: {}",
                    account.getId(),
                    exception.getReason()
            );
            return Optional.empty();
        }
    }

    private List<RecentGameResponse> buildRecentGamesFromDatabase(
            RiotAccount account,
            List<SeasonActivityRow> seasonRows
    ) {
        List<RecentGameResponse> games = new ArrayList<>(seasonRows.size());
        for (SeasonActivityRow row : seasonRows) {
            Integer championId = row.getChampionId() != null && row.getChampionId() > 0
                    ? row.getChampionId()
                    : null;
            games.add(new RecentGameResponse(
                    row.getMatchId(),
                    account.getRiotGameName(),
                    account.getRiotTagLine(),
                    championId,
                    championIconUrlService.buildApiPath(championId),
                    row.isWin(),
                    row.getGameStart()
            ));
        }
        return games;
    }

    private ChampionStatsResult buildSeasonChampionStatsFromRows(String riotPuuid, List<SeasonActivityRow> rows) {
        Map<Integer, ChampionAccumulator> championStats = new HashMap<>();
        ChampionAccumulator overall = new ChampionAccumulator();

        for (SeasonActivityRow row : rows) {
            ParticipantSnapshot snapshot = toParticipantSnapshot(row);
            overall.add(snapshot);
            if (snapshot.championId() != null) {
                championStats
                        .computeIfAbsent(snapshot.championId(), ChampionAccumulator::forChampion)
                        .add(snapshot);
            }
        }

        Map<Integer, ChampionRankRow> ranks = accountMatchRepository
                .findChampionRanks(riotPuuid, leaderboardProperties.seasonStartAt(), CHAMPION_RANK_MIN_GAMES)
                .stream()
                .collect(Collectors.toMap(ChampionRankRow::getChampionId, row -> row, (a, b) -> a));

        return new ChampionStatsResult(buildChampionStats(overall, championStats, ranks), buildPlaystyle(overall));
    }

    private PlaystyleResponse buildPlaystyle(ChampionAccumulator overall) {
        if (overall.games == 0 || overall.totalDurationSeconds <= 0) {
            return null;
        }
        double per10Minutes = overall.totalDurationSeconds / 600.0;
        double kda = overall.deaths > 0
                ? (double) (overall.kills + overall.assists) / overall.deaths
                : overall.kills + overall.assists;
        double farmPerMin = overall.cs / (overall.totalDurationSeconds / 60.0);
        double aggressionPer10 = overall.kills / per10Minutes;
        double resiliencePer10 = Math.max(0.0, 10.0 - overall.deaths / per10Minutes);
        double soloCarryIndex = (double) overall.kills / Math.max(overall.kills + overall.assists, 1);

        return new PlaystyleResponse(
                roundTwo(kda),
                roundTwo(farmPerMin),
                roundTwo(aggressionPer10),
                roundTwo(resiliencePer10),
                roundTwo(soloCarryIndex),
                normalize(kda, 6.0),
                normalize(farmPerMin, 10.0),
                normalize(aggressionPer10, 10.0),
                normalize(resiliencePer10, 10.0),
                normalize(soloCarryIndex, 1.0)
        );
    }

    private static double normalize(double value, double domainMax) {
        return roundTwo(100.0 * Math.max(0.0, Math.min(value, domainMax)) / domainMax);
    }

    private static double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ChampionStatsResult(List<ChampionStatResponse> champions, PlaystyleResponse playstyle) {
        static final ChampionStatsResult EMPTY = new ChampionStatsResult(List.of(), null);
    }

    private ParticipantSnapshot toParticipantSnapshot(SeasonActivityRow row) {
        Integer championId = row.getChampionId() != null && row.getChampionId() > 0
                ? row.getChampionId()
                : null;
        String championName = row.getChampionName();
        if (championName != null && championName.isBlank()) {
            championName = null;
        }

        boolean hasCombatStats = row.getKills() != null
                && row.getDeaths() != null
                && row.getAssists() != null
                && row.getCs() != null
                && row.getGameDurationSeconds() != null;

        return new ParticipantSnapshot(
                championId,
                championName,
                row.isWin(),
                hasCombatStats,
                hasCombatStats ? row.getKills() : 0,
                hasCombatStats ? row.getDeaths() : 0,
                hasCombatStats ? row.getAssists() : 0,
                hasCombatStats ? row.getCs() : 0,
                hasCombatStats ? RiotMatchDurations.normalizeSeconds(row.getGameDurationSeconds()) : 0L
        );
    }

    private List<ChampionStatResponse> buildChampionStats(
            ChampionAccumulator overall,
            Map<Integer, ChampionAccumulator> byChampion,
            Map<Integer, ChampionRankRow> ranks
    ) {
        if (overall.games == 0) {
            return List.of();
        }

        List<ChampionStatResponse> champions = new ArrayList<>();
        champions.add(overall.toResponse(null, null, null, null, null));

        byChampion.values().stream()
                .sorted(Comparator.<ChampionAccumulator>comparingInt(accumulator -> accumulator.games).reversed())
                .map(accumulator -> {
                    ChampionRankRow rankRow = ranks.get(accumulator.championId);
                    return accumulator.toResponse(
                            accumulator.championId,
                            championIconUrlService.buildApiPath(accumulator.championId),
                            accumulator.championName,
                            rankRow != null ? rankRow.getRank() : null,
                            rankRow != null ? rankRow.getPoolSize() : null
                    );
                })
                .forEach(champions::add);

        return champions;
    }

    private record ParticipantSnapshot(
            Integer championId,
            String championName,
            boolean win,
            boolean hasCombatStats,
            int kills,
            int deaths,
            int assists,
            int cs,
            long gameDurationSeconds
    ) {
    }

    private static final class ChampionAccumulator {
        private Integer championId;
        private String championName;
        private int games;
        private int wins;
        private int kills;
        private int deaths;
        private int assists;
        private int cs;
        private long totalDurationSeconds;
        private int statGames;

        static ChampionAccumulator forChampion(Integer championId) {
            ChampionAccumulator accumulator = new ChampionAccumulator();
            accumulator.championId = championId;
            return accumulator;
        }

        void add(ParticipantSnapshot snapshot) {
            if (championName == null && snapshot.championName() != null) {
                championName = snapshot.championName();
            }
            games++;
            if (snapshot.win()) {
                wins++;
            }
            if (!snapshot.hasCombatStats()) {
                return;
            }
            statGames++;
            kills += snapshot.kills();
            deaths += snapshot.deaths();
            assists += snapshot.assists();
            cs += snapshot.cs();
            totalDurationSeconds += Math.max(snapshot.gameDurationSeconds(), 0L);
        }

        ChampionStatResponse toResponse(
                Integer championId,
                String championIconUrl,
                String championName,
                Integer rank,
                Integer rankPoolSize
        ) {
            double winRate = games > 0 ? (double) wins / games : 0.0;
            double avgKills = statGames > 0 ? (double) kills / statGames : 0.0;
            double avgDeaths = statGames > 0 ? (double) deaths / statGames : 0.0;
            double avgAssists = statGames > 0 ? (double) assists / statGames : 0.0;
            double kda = deaths > 0 ? (double) (kills + assists) / deaths : kills + assists;
            int avgCs = statGames > 0 ? Math.round((float) cs / statGames) : 0;
            double durationMinutes = totalDurationSeconds / 60.0;
            double avgCsPerMin = durationMinutes > 0 ? cs / durationMinutes : 0.0;

            return new ChampionStatResponse(
                    championId,
                    championIconUrl,
                    championName,
                    games,
                    wins,
                    winRate,
                    roundOneDecimal(avgKills),
                    roundOneDecimal(avgDeaths),
                    roundOneDecimal(avgAssists),
                    roundTwoDecimals(kda),
                    avgCs,
                    roundOneDecimal(avgCsPerMin),
                    rank,
                    rankPoolSize
            );
        }

        private static double roundOneDecimal(double value) {
            return Math.round(value * 10.0) / 10.0;
        }

        private static double roundTwoDecimals(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }
}
