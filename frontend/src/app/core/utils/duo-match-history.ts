import { DuoMatchHistory, ParticipantMatchHistory } from '../models/challenge.models';

export function duoPlayer1MatchHistory(matches: DuoMatchHistory[]): ParticipantMatchHistory[] {
  return matches.map((match) => ({
    matchId: match.matchId,
    championId: match.player1ChampionId,
    championIconUrl: match.player1ChampionIconUrl,
    win: match.win,
    lpDelta: match.player1LpDelta,
    playedAt: match.playedAt,
  }));
}

export function duoPlayer2MatchHistory(matches: DuoMatchHistory[]): ParticipantMatchHistory[] {
  return matches.map((match) => ({
    matchId: match.matchId,
    championId: match.player2ChampionId,
    championIconUrl: match.player2ChampionIconUrl,
    win: match.win,
    lpDelta: match.player2LpDelta,
    playedAt: match.playedAt,
  }));
}
