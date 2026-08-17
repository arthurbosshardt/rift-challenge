import { describe, expect, it } from 'vitest';
import {
  sortDirectionArrow,
  sortDuos,
  sortParticipants,
  isLeaderboardLeader,
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
    expect(sorted[1].position).toBe(2);
  });

  it('sorts participants by LP gained ascending', () => {
    const sorted = sortParticipants(
      [
        participant({ id: 'low', lpGained: 10 }),
        participant({ id: 'high', lpGained: 80 }),
      ],
      'LP_GAIN',
      'asc',
    );
    expect(sorted.map((item) => item.id)).toEqual(['low', 'high']);
    expect(sorted.map((item) => item.position)).toEqual([1, 2]);
  });

  it('reassigns rank numbers when direction changes', () => {
    const participants = [
      participant({ id: 'a', lpGained: 30 }),
      participant({ id: 'b', lpGained: 60 }),
      participant({ id: 'c', lpGained: 10 }),
    ];

    const descending = sortParticipants(participants, 'LP_GAIN', 'desc');
    const ascending = sortParticipants(participants, 'LP_GAIN', 'asc');

    expect(descending.map((item) => item.id)).toEqual(['b', 'a', 'c']);
    expect(ascending.map((item) => item.id)).toEqual(['c', 'a', 'b']);
    expect(descending.map((item) => item.position)).toEqual([1, 2, 3]);
    expect(ascending.map((item) => item.position)).toEqual([1, 2, 3]);
  });

  it('reverses tied participants when direction changes', () => {
    const participants = [
      participant({ id: 'a', riotId: 'Alpha#EUW', lpGained: 0 }),
      participant({ id: 'b', riotId: 'Bravo#EUW', lpGained: 0 }),
      participant({ id: 'c', riotId: 'Charlie#EUW', lpGained: 0 }),
    ];

    const descending = sortParticipants(participants, 'LP_GAIN', 'desc');
    const ascending = sortParticipants(participants, 'LP_GAIN', 'asc');

    expect(descending.map((item) => item.riotId)).toEqual(['Alpha#EUW', 'Bravo#EUW', 'Charlie#EUW']);
    expect(ascending.map((item) => item.riotId)).toEqual(['Charlie#EUW', 'Bravo#EUW', 'Alpha#EUW']);
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

  it('highlights only the first row in descending order', () => {
    expect(isLeaderboardLeader(0, 'desc')).toBe(true);
    expect(isLeaderboardLeader(1, 'desc')).toBe(false);
    expect(isLeaderboardLeader(0, 'asc')).toBe(false);
  });
});
