import { Injectable, signal } from '@angular/core';
import {
  AccountRecentGames,
  ChallengeSummary,
  ParticipantMatchHistory,
  RecentGameResponse,
} from '../models/challenge.models';

export interface ActivityAccount extends AccountRecentGames {
  matches: ParticipantMatchHistory[];
  syncRemainingBaseline: number;
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

const STORAGE_KEY = 'riftchallenge.activityCache.v7';

type ActivityAccountSource = AccountRecentGames & {
  matches?: ParticipantMatchHistory[];
  games?: RecentGameResponse[];
};

export function normalizeActivityAccount(account: ActivityAccountSource): ActivityAccount {
  const wins = account.wins ?? 0;
  const losses = account.losses ?? 0;
  const seasonGames = account.seasonGames ?? wins + losses;
  const syncedGames = account.syncedGames ?? 0;
  const seasonSyncComplete =
    account.seasonSyncComplete ?? (seasonGames > 0 && syncedGames >= seasonGames);
  const seasonSyncInProgress = account.seasonSyncInProgress ?? false;

  const matches =
    account.matches ??
    (account.games ?? []).map((game) => ({
      matchId: game.id,
      championId: game.championId,
      championIconUrl: game.championIconUrl,
      win: game.win,
      lpDelta: 0,
      playedAt: game.playedAt,
    }));

  return {
    ...account,
    champions: account.champions ?? [],
    playstyle: account.playstyle ?? null,
    syncedGames,
    seasonGames,
    seasonSyncComplete,
    seasonSyncInProgress,
    syncRemainingBaseline: Math.max(0, seasonGames - syncedGames),
    matches,
  };
}

export function applySyncBaseline(
  account: ActivityAccount,
  resetBaseline: boolean,
  previous?: ActivityAccount,
): ActivityAccount {
  const remaining = Math.max(0, account.seasonGames - account.syncedGames);
  if (account.seasonSyncComplete) {
    return { ...account, syncRemainingBaseline: 0 };
  }

  const baseline =
    resetBaseline || previous?.syncRemainingBaseline == null
      ? remaining
      : previous.syncRemainingBaseline;

  return { ...account, syncRemainingBaseline: baseline };
}

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

    this.activityAccounts.set(
      (stored.activityAccounts ?? []).map((account) => {
        const normalized = normalizeActivityAccount(account);
        const remaining = Math.max(0, normalized.seasonGames - normalized.syncedGames);
        return { ...normalized, syncRemainingBaseline: remaining };
      }),
    );
    this.activityLastLoadedAt.set(stored.activityLastLoadedAt);
    this.lastRefreshedAt.set(stored.lastRefreshedAt);
    this.challenges.set(stored.challenges ?? []);
    this.challengesLastLoadedAt.set(stored.challengesLastLoadedAt);
    this.challengesGeneratedAt.set(stored.challengesGeneratedAt);
  }

  setActivity(accounts: ActivityAccount[], refreshedAt: string, resetSyncBaseline = false): void {
    const previousById = new Map(this.activityAccounts().map((account) => [account.accountId, account]));
    this.activityAccounts.set(
      accounts
        .map((account) => normalizeActivityAccount(account))
        .map((account) => applySyncBaseline(account, resetSyncBaseline, previousById.get(account.accountId))),
    );
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

  /** Drop cached activity after linking a new Riot account so Mes statistiques refetches sync state. */
  invalidateActivity(): void {
    this.activityAccounts.set([]);
    this.activityLastLoadedAt.set(null);
    this.lastRefreshedAt.set(null);
    this.persist();
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
