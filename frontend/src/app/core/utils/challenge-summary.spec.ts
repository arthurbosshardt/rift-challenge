import { describe, expect, it } from 'vitest';
import { ChallengeDetail, ChallengeSummary } from '../models/challenge.models';
import {
  enrichSummaryFromDetail,
  normalizeChallengeSummary,
  CHALLENGE_PREVIEW_FETCH_LIMIT,
  summaryChallengeNeedsEnrichment,
  summaryChallengeNeedsStatsRefresh,
} from './challenge-summary';

describe('challenge-summary utils', () => {
  it('normalizeChallengeSummary falls back participantGameNames from preview', () => {
    const summary = normalizeChallengeSummary({
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
    });

    expect(summary.participantGameNames).toEqual(['JohnDoe']);
  });

  it('summaryChallengeNeedsEnrichment detects incomplete names and preview', () => {
    expect(
      summaryChallengeNeedsEnrichment({
        entryCount: 6,
        participantGameNames: ['a', 'b', 'c', 'd', 'e'],
        previewParticipants: [{ gameName: 'a' } as ChallengeSummary['previewParticipants'][number]],
        type: 'SOLOQ',
      }),
    ).toBe(true);

    expect(
      summaryChallengeNeedsEnrichment({
        entryCount: 2,
        participantGameNames: ['a', 'b'],
        previewParticipants: [
          { gameName: 'a' } as ChallengeSummary['previewParticipants'][number],
          { gameName: 'b' } as ChallengeSummary['previewParticipants'][number],
        ],
        type: 'SOLOQ',
      }),
    ).toBe(false);
  });

  it('enrichSummaryFromDetail fills participantGameNames and preview from detail', () => {
    const summary: ChallengeSummary = {
      id: '1',
      shareSlug: 'slug',
      name: 'Test',
      type: 'SOLOQ',
      startAt: '2026-01-01T00:00:00Z',
      endAt: null,
      maxGames: null,
      isPublic: true,
      status: 'ACTIVE',
      entryCount: 1,
      participantGameNames: [],
      previewParticipants: [],
      previewDuos: [],
    };

    const detail: ChallengeDetail = {
      ...summary,
      sharePath: '/challenges/slug',
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
        rankEstimated: false,
        recentMatches: [],
      })),
      duos: [],
      refreshAvailable: true,
      lastRefreshedAt: null,
      nextRefreshAvailableAt: null,
    };

    const enriched = enrichSummaryFromDetail(summary, detail);

    expect(enriched.participantGameNames).toHaveLength(12);
    expect(enriched.previewParticipants).toHaveLength(CHALLENGE_PREVIEW_FETCH_LIMIT);
    expect(enriched.entryCount).toBe(12);
  });

  it('summaryChallengeNeedsStatsRefresh targets active and finished challenges', () => {
    expect(summaryChallengeNeedsStatsRefresh({ status: 'NOT_STARTED' })).toBe(false);
    expect(summaryChallengeNeedsStatsRefresh({ status: 'ACTIVE' })).toBe(true);
    expect(summaryChallengeNeedsStatsRefresh({ status: 'FINISHED' })).toBe(true);
  });
});
