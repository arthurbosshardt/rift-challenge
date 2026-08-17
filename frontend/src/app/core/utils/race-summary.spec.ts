import { describe, expect, it } from 'vitest';
import { RaceDetail, RaceSummary } from '../models/race.models';
import {
  enrichSummaryFromDetail,
  normalizeRaceSummary,
  RACE_PREVIEW_FETCH_LIMIT,
  resolveRaceCardPreviewLimit,
  summaryRaceNeedsEnrichment,
} from './race-summary';

describe('race-summary utils', () => {
  it('resolveRaceCardPreviewLimit scales with viewport width', () => {
    expect(resolveRaceCardPreviewLimit(500)).toBe(3);
    expect(resolveRaceCardPreviewLimit(900)).toBe(6);
    expect(resolveRaceCardPreviewLimit(1700)).toBe(10);
  });

  it('normalizeRaceSummary falls back participantGameNames from preview', () => {
    const summary = normalizeRaceSummary({
      id: '1',
      shareSlug: 'slug',
      name: 'Test',
      type: 'SOLOQ',
      startAt: '2026-01-01T00:00:00Z',
      isPublic: true,
      status: 'ACTIVE',
      entryCount: 1,
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
    });

    expect(summary.participantGameNames).toEqual(['Tanor']);
  });

  it('summaryRaceNeedsEnrichment detects incomplete names and preview', () => {
    expect(
      summaryRaceNeedsEnrichment({
        entryCount: 6,
        participantGameNames: ['a', 'b', 'c', 'd', 'e'],
        previewParticipants: [{ gameName: 'a' } as RaceSummary['previewParticipants'][number]],
        type: 'SOLOQ',
      }),
    ).toBe(true);

    expect(
      summaryRaceNeedsEnrichment({
        entryCount: 2,
        participantGameNames: ['a', 'b'],
        previewParticipants: [
          { gameName: 'a' } as RaceSummary['previewParticipants'][number],
          { gameName: 'b' } as RaceSummary['previewParticipants'][number],
        ],
        type: 'SOLOQ',
      }),
    ).toBe(false);
  });

  it('enrichSummaryFromDetail fills participantGameNames and preview from detail', () => {
    const summary: RaceSummary = {
      id: '1',
      shareSlug: 'slug',
      name: 'Test',
      type: 'SOLOQ',
      startAt: '2026-01-01T00:00:00Z',
      endAt: null,
      isPublic: true,
      status: 'ACTIVE',
      entryCount: 1,
      participantGameNames: [],
      previewParticipants: [],
      previewDuos: [],
    };

    const detail: RaceDetail = {
      ...summary,
      sharePath: '/race/slug',
      isOwner: false,
      participants: Array.from({ length: 12 }, (_, index) => ({
        id: `p${index}`,
        gameName: `Player${index}`,
        tagLine: 'EUW',
        riotId: `Player${index}#EUW`,
        position: index + 1,
        currentTier: null,
        currentRank: null,
        currentLp: 0,
        lpGained: 0,
        rankScore: 0,
        wins: 0,
        losses: 0,
        winRate: 0,
        profileIconId: null,
        hasRankData: false,
      })),
      duos: [],
      refreshAvailable: true,
      lastRefreshedAt: null,
      nextRefreshAvailableAt: null,
    };

    const enriched = enrichSummaryFromDetail(summary, detail);

    expect(enriched.participantGameNames).toHaveLength(12);
    expect(enriched.previewParticipants).toHaveLength(RACE_PREVIEW_FETCH_LIMIT);
    expect(enriched.entryCount).toBe(12);
  });
});
