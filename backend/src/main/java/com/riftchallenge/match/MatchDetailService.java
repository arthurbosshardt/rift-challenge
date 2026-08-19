package com.riftchallenge.match;

import com.riftchallenge.match.dto.MatchDetailResponse;
import com.riftchallenge.match.dto.MatchItemResponse;
import com.riftchallenge.match.dto.MatchParticipantResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.SummonerSpellIconUrlService;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchDetailService {

    private static final String DDRAGON_VERSION = "16.16.1";
    private static final List<String> ROLE_ORDER = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");

    private final RiotMatchClient riotMatchClient;
    private final ChampionIconUrlService championIconUrlService;
    private final SummonerSpellIconUrlService summonerSpellIconUrlService;

    public MatchDetailService(
            RiotMatchClient riotMatchClient,
            ChampionIconUrlService championIconUrlService,
            SummonerSpellIconUrlService summonerSpellIconUrlService
    ) {
        this.riotMatchClient = riotMatchClient;
        this.championIconUrlService = championIconUrlService;
        this.summonerSpellIconUrlService = summonerSpellIconUrlService;
    }

    public MatchDetailResponse buildMatchDetail(String matchId, String focusPuuid) {
        RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);

        RiotMatchDetailDto.Participant focus = match.info().participants().stream()
                .filter(participant -> focusPuuid.equals(participant.puuid()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in this match"));

        List<MatchParticipantResponse> myTeam = buildTeam(match, focus.teamId(), focusPuuid);
        List<MatchParticipantResponse> enemyTeam = buildTeam(match, focus.teamId() == 100 ? 200 : 100, focusPuuid);

        return new MatchDetailResponse(
                matchId,
                Instant.ofEpochMilli(match.info().gameStartTimestamp()),
                match.info().gameDuration(),
                focus.win(),
                myTeam,
                enemyTeam
        );
    }

    private List<MatchParticipantResponse> buildTeam(RiotMatchDetailDto match, int teamId, String focusPuuid) {
        return match.info().participants().stream()
                .filter(participant -> participant.teamId() == teamId)
                .sorted(Comparator.comparingInt(participant -> roleIndex(participant.teamPosition())))
                .map(participant -> toParticipantResponse(participant, focusPuuid))
                .toList();
    }

    private int roleIndex(String role) {
        int index = ROLE_ORDER.indexOf(role);
        return index < 0 ? ROLE_ORDER.size() : index;
    }

    private MatchParticipantResponse toParticipantResponse(RiotMatchDetailDto.Participant participant, String focusPuuid) {
        return new MatchParticipantResponse(
                participant.riotIdGameName(),
                participant.riotIdTagline(),
                participant.profileIcon(),
                participant.championId(),
                championIconUrlService.buildApiPath(participant.championId()),
                participant.champLevel(),
                participant.teamPosition(),
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
                "https://ddragon.leagueoflegends.com/cdn/%s/img/item/%d.png".formatted(DDRAGON_VERSION, itemId)
        );
    }
}
