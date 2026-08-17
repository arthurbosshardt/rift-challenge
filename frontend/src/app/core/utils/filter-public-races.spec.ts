import { describe, expect, it } from 'vitest';
import { RaceSummary } from '../models/race.models';
import {
  filterPublicRaces,
  hasActivePublicRaceFilters,
  normalizeSummonerSearch,
} from './filter-public-races';

function race(overrides: Partial<RaceSummary> = {}): RaceSummary {
  return {
    id: 'race-1',
    shareSlug: 'slug-1',
    name: 'Les petits soldats',
    type: 'SOLOQ',
    startAt: '2026-01-01T00:00:00Z',
    endAt: '2026-02-01T00:00:00Z',
    isPublic: true,
    status: 'ACTIVE',
    entryCount: 2,
    participantGameNames: ['Kaori', 'Tanor'],
    previewParticipants: [
      {
        id: 'p1',
        gameName: 'Kaori',
        tagLine: 'EUW',
        riotId: 'Kaori#EUW',
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

describe('filter-public-races', () => {
  it('normalizeSummonerSearch strips tag and spaces', () => {
    expect(normalizeSummonerSearch('Tanor#7154')).toBe('tanor');
    expect(normalizeSummonerSearch('Hide on bush')).toBe('hideonbush');
  });

  it('filters by race name when query has at least 3 characters', () => {
    const races = [
      race(),
      race({ id: 'race-2', shareSlug: 'slug-2', name: 'Autre course' }),
    ];

    expect(filterPublicRaces(races, { raceName: 'sold', summoner: '', type: 'ALL' })).toHaveLength(1);
    expect(filterPublicRaces(races, { raceName: 'so', summoner: '', type: 'ALL' })).toHaveLength(2);
  });

  it('filters by summoner using participantGameNames', () => {
    const races = [
      race(),
      race({
        id: 'race-2',
        shareSlug: 'slug-2',
        name: 'Sans Tanor',
        participantGameNames: ['Kaori'],
      }),
    ];

    expect(filterPublicRaces(races, { raceName: '', summoner: 'tanor', type: 'ALL' })).toHaveLength(1);
  });

  it('falls back to preview names when participantGameNames is empty', () => {
    const races = [
      race({
        participantGameNames: [],
        previewParticipants: [
          {
            id: 'p1',
            gameName: 'Tanor',
            tagLine: '7154',
            riotId: 'Tanor#7154',
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

    expect(filterPublicRaces(races, { raceName: '', summoner: 'tanor', type: 'ALL' })).toHaveLength(1);
  });

  it('filters by race type', () => {
    const races = [
      race({ type: 'SOLOQ' }),
      race({ id: 'race-2', shareSlug: 'slug-2', name: 'Duo race', type: 'DUOQ' }),
    ];

    expect(filterPublicRaces(races, { raceName: '', summoner: '', type: 'DUOQ' })).toHaveLength(1);
    expect(filterPublicRaces(races, { raceName: '', summoner: '', type: 'DUOQ' })[0].type).toBe('DUOQ');
  });

  it('hasActivePublicRaceFilters detects active filters', () => {
    expect(hasActivePublicRaceFilters({ raceName: '', summoner: '', type: 'ALL' })).toBe(false);
    expect(hasActivePublicRaceFilters({ raceName: 'abc', summoner: '', type: 'ALL' })).toBe(true);
    expect(hasActivePublicRaceFilters({ raceName: '', summoner: 'tan', type: 'ALL' })).toBe(true);
    expect(hasActivePublicRaceFilters({ raceName: '', summoner: '', type: 'SOLOQ' })).toBe(true);
  });
});
