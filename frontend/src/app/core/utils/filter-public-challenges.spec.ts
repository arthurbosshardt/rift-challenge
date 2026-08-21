import { describe, expect, it } from 'vitest';
import { ChallengeSummary } from '../models/challenge.models';
import {
  filterPublicChallenges,
  hasActivePublicChallengeFilters,
  normalizeSummonerSearch,
} from './filter-public-challenges';

function challenge(overrides: Partial<ChallengeSummary> = {}): ChallengeSummary {
  return {
    id: 'challenge-1',
    shareSlug: 'slug-1',
    name: 'Rush de rentrée 2025',
    type: 'SOLOQ',
    region: 'EUW',
    startAt: '2026-01-01T00:00:00Z',
    endAt: '2026-02-01T00:00:00Z',
    maxGames: null,
    status: 'ACTIVE',
    entryCount: 2,
    participantGameNames: ['JaneDoe', 'JohnDoe'],
    previewParticipants: [
      {
        id: 'p1',
        gameName: 'JaneDoe',
        tagLine: 'EUW',
        riotId: 'JaneDoe#EUW',
        profileIconId: null,
        currentTier: 'GOLD',
        currentRank: 'II',
        currentLp: 50,
        lpGained: 10,
        wins: 5,
        losses: 3,
        winRate: 62.5,
        position: 1,
      },
    ],
    previewDuos: [],
    ...overrides,
  };
}

describe('filter-public-challenges', () => {
  it('normalizeSummonerSearch strips tag and spaces', () => {
    expect(normalizeSummonerSearch('JohnDoe#EUW')).toBe('johndoe');
    expect(normalizeSummonerSearch('Hide on bush')).toBe('hideonbush');
  });

  it('filters by challenge name when query has at least 3 characters', () => {
    const challenges = [
      challenge(),
      challenge({ id: 'challenge-2', shareSlug: 'slug-2', name: 'Autre course' }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: 'rush', summoner: '', type: 'ALL', status: 'ALL', region: 'EUW' })).toHaveLength(1);
    expect(filterPublicChallenges(challenges, { challengeName: 'ru', summoner: '', type: 'ALL', status: 'ALL', region: 'EUW' })).toHaveLength(2);
  });

  it('filters by summoner using participantGameNames', () => {
    const challenges = [
      challenge(),
      challenge({
        id: 'challenge-2',
        shareSlug: 'slug-2',
        name: 'Sans JohnDoe',
        participantGameNames: ['JaneDoe'],
      }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: 'johndoe', type: 'ALL', status: 'ALL', region: 'EUW' })).toHaveLength(1);
  });

  it('falls back to preview names when participantGameNames is empty', () => {
    const challenges = [
      challenge({
        participantGameNames: [],
        previewParticipants: [
          {
            id: 'p1',
            gameName: 'JohnDoe',
            tagLine: 'EUW',
            riotId: 'JohnDoe#EUW',
            profileIconId: null,
            currentTier: null,
            currentRank: null,
            currentLp: 0,
            lpGained: 0,
            wins: 0,
            losses: 0,
            winRate: 0,
            position: 1,
          },
        ],
      }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: 'johndoe', type: 'ALL', status: 'ALL', region: 'EUW' })).toHaveLength(1);
  });

  it('filters by challenge type', () => {
    const challenges = [
      challenge({ type: 'SOLOQ' }),
      challenge({ id: 'challenge-2', shareSlug: 'slug-2', name: 'Duo challenge', type: 'DUOQ' }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'DUOQ', status: 'ALL', region: 'EUW' })).toHaveLength(1);
    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'DUOQ', status: 'ALL', region: 'EUW' })[0].type).toBe('DUOQ');
  });

  it('filters by challenge status', () => {
    const challenges = [
      challenge({ status: 'ACTIVE' }),
      challenge({ id: 'challenge-2', shareSlug: 'slug-2', name: 'Finished challenge', status: 'FINISHED' }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'ALL', status: 'FINISHED', region: 'EUW' })).toHaveLength(1);
    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'ALL', status: 'FINISHED', region: 'EUW' })[0].status).toBe('FINISHED');
  });

  it('filters by region', () => {
    const challenges = [
      challenge({ region: 'EUW' }),
      challenge({ id: 'challenge-2', shareSlug: 'slug-2', name: 'NA challenge', region: 'NA' }),
    ];

    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'ALL', status: 'ALL', region: 'NA' })).toHaveLength(1);
    expect(filterPublicChallenges(challenges, { challengeName: '', summoner: '', type: 'ALL', status: 'ALL', region: 'NA' })[0].region).toBe('NA');
  });

  it('hasActivePublicChallengeFilters detects active filters', () => {
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: '', type: 'ALL', status: 'ACTIVE', region: 'EUW' })).toBe(false);
    expect(hasActivePublicChallengeFilters({ challengeName: 'abc', summoner: '', type: 'ALL', status: 'ACTIVE', region: 'EUW' })).toBe(true);
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: 'jan', type: 'ALL', status: 'ACTIVE', region: 'EUW' })).toBe(true);
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: '', type: 'SOLOQ', status: 'ACTIVE', region: 'EUW' })).toBe(true);
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: '', type: 'ALL', status: 'FINISHED', region: 'EUW' })).toBe(true);
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: '', type: 'ALL', status: 'ALL', region: 'EUW' })).toBe(true);
    expect(hasActivePublicChallengeFilters({ challengeName: '', summoner: '', type: 'ALL', status: 'ACTIVE', region: 'NA' })).toBe(true);
  });
});
