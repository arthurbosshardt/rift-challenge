import { ActivityCacheService } from './activity-cache.service';
import { describe, expect, it, beforeEach, afterEach } from 'vitest';

describe('ActivityCacheService', () => {
  const ownerKey = 'user-1';

  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('hydrates activity from sessionStorage for the same owner', () => {
    const first = new ActivityCacheService();
    first.hydrateForOwner(ownerKey);
    first.setActivity(
      [
        {
          accountId: 'acc-1',
          gameName: 'Tanor',
          tagLine: 'EUW',
          profileIconId: 1,
          tier: 'GOLD',
          rank: 'II',
          leaguePoints: 50,
          wins: 10,
          losses: 5,
          games: [],
          matches: [],
        },
      ],
      '2026-08-21T10:00:00.000Z',
    );

    const second = new ActivityCacheService();
    expect(second.activityLastLoadedAt()).toBeNull();

    second.hydrateForOwner(ownerKey);
    expect(second.activityLastLoadedAt()).not.toBeNull();
    expect(second.activityAccounts()).toHaveLength(1);
    expect(second.activityAccounts()[0].gameName).toBe('Tanor');
    expect(second.lastRefreshedAt()).toBe('2026-08-21T10:00:00.000Z');
  });

  it('ignores cache belonging to another owner', () => {
    const first = new ActivityCacheService();
    first.hydrateForOwner(ownerKey);
    first.setActivity([], '2026-08-21T10:00:00.000Z');

    const second = new ActivityCacheService();
    second.hydrateForOwner('user-2');
    expect(second.activityLastLoadedAt()).toBeNull();
    expect(second.activityAccounts()).toEqual([]);
  });

  it('clear removes persisted cache', () => {
    const cache = new ActivityCacheService();
    cache.hydrateForOwner(ownerKey);
    cache.setActivity([], '2026-08-21T10:00:00.000Z');
    cache.clear();

    const restored = new ActivityCacheService();
    restored.hydrateForOwner(ownerKey);
    expect(restored.activityLastLoadedAt()).toBeNull();
  });
});
