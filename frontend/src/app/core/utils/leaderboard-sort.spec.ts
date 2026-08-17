import { describe, expect, it } from 'vitest';
import {
  sortDirectionArrow,
  sortDuos,
  sortParticipants,
} from './leaderboard-sort';
import { DuoProgress, ParticipantProgress } from '../models/race.models';

function participant(overrides: Partial<ParticipantProgress>): ParticipantProgress {
  return {
    id: 'p1',
    gameName: 'A',
    tagLine: 'EUW',
    riotId: 'A#EUW',
    position: 1,
    currentTier: 'GOLD',
    currentRank: 'I',
    currentLp: 50,
    lpGained: 0,
    rankScore: 0,
    wins: 0,
    losses: 0,
    winRate: 0,
    profileIconId: null,
    hasRankData: true,
    ...overrides,
  };
}

describe('leaderboard-sort', () => {
  it('sorts participants by LP gained descending', () => {
    const sorted = sortParticipants(
      [
        participant({ id: 'low', lpGained: 10 }),
        participant({ id: 'high', lpGained: 80 }),
      ],
      'LP_GAIN',
      'desc',
    );
    expect(sorted.map((item) => item.id)).toEqual(['high', 'low']);
    expect(sorted[0].position).toBe(1);
  });

  it('keeps ineligible duos after eligible ones', () => {
    const eligible: DuoProgress = {
      id: 'ok',
      player1: participant({ id: 'a' }),
      player2: participant({ id: 'b' }),
      combinedRankScore: 10,
      combinedLpGained: 5,
      wins: 1,
      losses: 0,
      winRate: 1,
      eligible: true,
      ineligibilityReason: null,
      position: 1,
    };
    const ineligible: DuoProgress = {
      ...eligible,
      id: 'ko',
      eligible: false,
      combinedRankScore: 999,
      ineligibilityReason: 'x',
    };
    const sorted = sortDuos([ineligible, eligible], 'RANK', 'desc');
    expect(sorted[0].id).toBe('ok');
    expect(sorted[1].id).toBe('ko');
  });

  it('returns a direction arrow', () => {
    expect(sortDirectionArrow('desc')).toBe('↓');
    expect(sortDirectionArrow('asc')).toBe('↑');
  });
});
