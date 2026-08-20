import { Injectable, signal } from '@angular/core';
import { AccountRecentGames, ChallengeSummary, ParticipantMatchHistory } from '../models/challenge.models';

export interface ActivityAccount extends AccountRecentGames {
  matches: ParticipantMatchHistory[];
}

interface PersistedActivityCache {
  ownerKey: string;
  activityAccounts: ActivityAccount[];
  activityLastLoadedAt: number | null;
  lastRefreshedAt: string | null;
  challenges: ChallengeSummary[];
  challengesLastLoadedAt: number | null;
  challengesGeneratedAt: string | null;
}

const STORAGE_KEY = 'riftchallenge.activityCache.v1';

@Injectable({ providedIn: 'root' })
export class ActivityCacheService {
  readonly activityAccounts = signal<ActivityAccount[]>([]);
  readonly activityLastLoadedAt = signal<number | null>(null);
  readonly lastRefreshedAt = signal<string | null>(null);

  readonly challenges = signal<ChallengeSummary[]>([]);
  readonly challengesLastLoadedAt = signal<number | null>(null);
  readonly challengesGeneratedAt = signal<string | null>(null);

  private hydratedOwnerKey: string | null = null;

  /** Restore session-scoped cache for this user. Safe to call multiple times. */
  hydrateForOwner(ownerKey: string): void {
    if (!ownerKey || this.hydratedOwnerKey === ownerKey) {
      return;
    }

    if (this.hydratedOwnerKey !== null && this.hydratedOwnerKey !== ownerKey) {
      this.resetMemory();
    }

    this.hydratedOwnerKey = ownerKey;
    const stored = readStorage();
    if (!stored || stored.ownerKey !== ownerKey) {
      return;
    }

    this.activityAccounts.set(stored.activityAccounts ?? []);
    this.activityLastLoadedAt.set(stored.activityLastLoadedAt);
    this.lastRefreshedAt.set(stored.lastRefreshedAt);
    this.challenges.set(stored.challenges ?? []);
    this.challengesLastLoadedAt.set(stored.challengesLastLoadedAt);
    this.challengesGeneratedAt.set(stored.challengesGeneratedAt);
  }

  setActivity(accounts: ActivityAccount[], refreshedAt: string): void {
    this.activityAccounts.set(accounts);
    this.activityLastLoadedAt.set(Date.now());
    this.lastRefreshedAt.set(refreshedAt);
    this.persist();
  }

  setChallenges(challenges: ChallengeSummary[], generatedAt: string | null): void {
    this.challenges.set(challenges);
    this.challengesLastLoadedAt.set(Date.now());
    this.challengesGeneratedAt.set(generatedAt);
    this.persist();
  }

  removeChallenge(challengeId: string): void {
    this.challenges.update((challenges) => challenges.filter((c) => c.id !== challengeId));
    this.persist();
  }

  clear(): void {
    this.resetMemory();
    this.hydratedOwnerKey = null;
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* private mode / quota */
    }
  }

  private persist(): void {
    if (!this.hydratedOwnerKey) {
      return;
    }
    const payload: PersistedActivityCache = {
      ownerKey: this.hydratedOwnerKey,
      activityAccounts: this.activityAccounts(),
      activityLastLoadedAt: this.activityLastLoadedAt(),
      lastRefreshedAt: this.lastRefreshedAt(),
      challenges: this.challenges(),
      challengesLastLoadedAt: this.challengesLastLoadedAt(),
      challengesGeneratedAt: this.challengesGeneratedAt(),
    };
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    } catch {
      /* private mode / quota */
    }
  }

  private resetMemory(): void {
    this.activityAccounts.set([]);
    this.activityLastLoadedAt.set(null);
    this.lastRefreshedAt.set(null);
    this.challenges.set([]);
    this.challengesLastLoadedAt.set(null);
    this.challengesGeneratedAt.set(null);
  }
}

function readStorage(): PersistedActivityCache | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as PersistedActivityCache;
  } catch {
    return null;
  }
}
