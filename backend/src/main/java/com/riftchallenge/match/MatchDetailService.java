package com.riftchallenge.match;

import com.riftchallenge.match.dto.MatchDetailResponse;
import com.riftchallenge.match.dto.MatchItemResponse;
import com.riftchallenge.match.dto.MatchParticipantResponse;
import com.riftchallenge.match.dto.MatchTeamObjectivesResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.DDragonVersions;
import com.riftchallenge.riot.ProfileIconLoader;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RuneIconUrlService;
import com.riftchallenge.riot.SummonerSpellIconUrlService;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchDetailService {

    private static final List<String> ROLE_ORDER = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");

    private final RiotMatchClient riotMatchClient;
    private final RiotLeagueClient riotLeagueClient;
    private final ChampionIconUrlService championIconUrlService;
    private final SummonerSpellIconUrlService summonerSpellIconUrlService;
    private final RuneIconUrlService runeIconUrlService;
    private final ProfileIconLoader profileIconLoader;

    public MatchDetailService(
            RiotMatchClient riotMatchClient,
            RiotLeagueClient riotLeagueClient,
            ChampionIconUrlService championIconUrlService,
            SummonerSpellIconUrlService summonerSpellIconUrlService,
            RuneIconUrlService runeIconUrlService,
            ProfileIconLoader profileIconLoader
    ) {
        this.riotMatchClient = riotMatchClient;
        this.riotLeagueClient = riotLeagueClient;
        this.championIconUrlService = championIconUrlService;
        this.summonerSpellIconUrlService = summonerSpellIconUrlService;
        this.runeIconUrlService = runeIconUrlService;
        this.profileIconLoader = profileIconLoader;
    }

    public MatchDetailResponse buildMatchDetail(String matchId, String focusPuuid) {
        RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);

        RiotMatchDetailDto.Participant focus = match.info().participants().stream()
                .filter(participant -> focusPuuid.equals(participant.puuid()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in this match"));

        Map<String, RiotLeagueEntryDto> ranksByPuuid = fetchRanks(match.info().participants());

        List<MatchParticipantResponse> myTeam = buildTeam(match, focus.teamId(), focusPuuid, ranksByPuuid);
        List<MatchParticipantResponse> enemyTeam =
                buildTeam(match, focus.teamId() == 100 ? 200 : 100, focusPuuid, ranksByPuuid);

        return new MatchDetailResponse(
                matchId,
                Instant.ofEpochMilli(match.info().gameStartTimestamp()),
                match.info().gameDuration(),
                focus.win(),
                myTeam,
                enemyTeam,
                buildTeamObjectives(match, focus.teamId()),
                buildTeamObjectives(match, focus.teamId() == 100 ? 200 : 100)
        );
    }

    private MatchTeamObjectivesResponse buildTeamObjectives(RiotMatchDetailDto match, int teamId) {
        RiotMatchDetailDto.Objectives objectives = match.info().teams() == null
                ? null
                : match.info().teams().stream()
                        .filter(team -> team.teamId() == teamId)
                        .map(RiotMatchDetailDto.Team::objectives)
                        .findFirst()
                        .orElse(null);
        if (objectives == null) {
            return new MatchTeamObjectivesResponse(0, 0, 0, 0, 0);
        }
        return new MatchTeamObjectivesResponse(
                objectiveKills(objectives.tower()),
                objectiveKills(objectives.dragon()),
                objectiveKills(objectives.baron()),
                objectiveKills(objectives.riftHerald()),
                objectiveKills(objectives.inhibitor())
        );
    }

    private int objectiveKills(RiotMatchDetailDto.Objective objective) {
        return objective == null ? 0 : objective.kills();
    }

    private Map<String, RiotLeagueEntryDto> fetchRanks(List<RiotMatchDetailDto.Participant> participants) {
        Map<String, RiotLeagueEntryDto> ranksByPuuid = new ConcurrentHashMap<>();
        participants.parallelStream().forEach(participant -> {
            try {
                riotLeagueClient.findRankedSoloEntry(participant.puuid())
                        .ifPresent(entry -> ranksByPuuid.put(participant.puuid(), entry));
            } catch (ResponseStatusException exception) {
                // Rank badges are a nice-to-have: skip a player's rank rather than failing the whole match detail.
            }
        });
        return ranksByPuuid;
    }

    private List<MatchParticipantResponse> buildTeam(
            RiotMatchDetailDto match,
            int teamId,
            String focusPuuid,
            Map<String, RiotLeagueEntryDto> ranksByPuuid
    ) {
        return match.info().participants().stream()
                .filter(participant -> participant.teamId() == teamId)
                .sorted(Comparator.comparingInt(participant -> roleIndex(participant.teamPosition())))
                .map(participant -> toParticipantResponse(participant, focusPuuid, ranksByPuuid.get(participant.puuid())))
                .toList();
    }

    private int roleIndex(String role) {
        int index = ROLE_ORDER.indexOf(role);
        return index < 0 ? ROLE_ORDER.size() : index;
    }

    private MatchParticipantResponse toParticipantResponse(
            RiotMatchDetailDto.Participant participant,
            String focusPuuid,
            RiotLeagueEntryDto rank
    ) {
        return new MatchParticipantResponse(
                participant.riotIdGameName(),
                participant.riotIdTagline(),
                participant.profileIcon(),
                participant.profileIcon() != null && participant.profileIcon() > 0
                        ? profileIconLoader.buildUrl(participant.profileIcon())
                        : null,
                participant.championId(),
                championIconUrlService.buildApiPath(participant.championId()),
                participant.champLevel(),
                participant.teamPosition(),
                participant.teamId(),
                participant.win(),
                participant.kills(),
                participant.deaths(),
                participant.assists(),
                participant.totalMinionsKilled() + participant.neutralMinionsKilled(),
                participant.goldEarned(),
                participant.totalDamageDealtToChampions(),
                participant.visionScore(),
                participant.wardsPlaced(),
                summonerSpellIconUrlService.buildIconUrl(participant.summoner1Id()),
                summonerSpellIconUrlService.buildIconUrl(participant.summoner2Id()),
                runeIconUrlService.primaryRuneIconUrl(participant.primaryKeystonePerkId()),
                runeIconUrlService.treeIconUrl(participant.secondaryStyleId()),
                rank == null ? null : rank.tier(),
                rank == null ? null : rank.rank(),
                rank == null ? null : rank.leaguePoints(),
                buildItems(participant),
                focusPuuid.equals(participant.puuid())
        );
    }

    private List<MatchItemResponse> buildItems(RiotMatchDetailDto.Participant participant) {
        return Stream.of(
                        participant.item0(), participant.item1(), participant.item2(), participant.item3(),
                        participant.item4(), participant.item5(), participant.item6()
                )
                .map(this::toItemResponse)
                .toList();
    }

    private MatchItemResponse toItemResponse(int itemId) {
        if (itemId <= 0) {
            return new MatchItemResponse(null, null);
        }
        return new MatchItemResponse(
                itemId,
                "https://ddragon.leagueoflegends.com/cdn/%s/img/item/%d.png".formatted(DDragonVersions.CURRENT, itemId)
        );
    }
}
