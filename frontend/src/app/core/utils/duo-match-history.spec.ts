import { DuoMatchHistory } from '../models/challenge.models';
import { duoPlayer1MatchHistory, duoPlayer2MatchHistory } from './duo-match-history';

describe('duoMatchHistory', () => {
  const matches: DuoMatchHistory[] = [
    {
      win: true,
      player1ChampionId: 103,
      player1ChampionIconUrl: '/api/champion-icons/103.png',
      player1LpDelta: 20,
      player2ChampionId: 86,
      player2ChampionIconUrl: '/api/champion-icons/86.png',
      player2LpDelta: 18,
      playedAt: '2026-08-17T12:00:00Z',
    },
  ];

  it('splits duo history per player', () => {
    expect(duoPlayer1MatchHistory(matches)).toEqual([
      {
        championId: 103,
        championIconUrl: '/api/champion-icons/103.png',
        win: true,
        lpDelta: 20,
        playedAt: '2026-08-17T12:00:00Z',
      },
    ]);
    expect(duoPlayer2MatchHistory(matches)).toEqual([
      {
        championId: 86,
        championIconUrl: '/api/champion-icons/86.png',
        win: true,
        lpDelta: 18,
        playedAt: '2026-08-17T12:00:00Z',
      },
    ]);
  });
});
