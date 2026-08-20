import { Injectable, signal } from '@angular/core';
import { AccountRecentGames, ChallengeSummary, ParticipantMatchHistory } from '../models/challenge.models';

export interface ActivityAccount extends AccountRecentGames {
  matches: ParticipantMatchHistory[];
}

@Injectable({ providedIn: 'root' })
export class ActivityCacheService {
  readonly activityAccounts = signal<ActivityAccount[]>([]);
  readonly activityLastLoadedAt = signal<number | null>(null);
  readonly lastRefreshedAt = signal<string | null>(null);

  readonly challenges = signal<ChallengeSummary[]>([]);
  readonly challengesLastLoadedAt = signal<number | null>(null);
  readonly challengesGeneratedAt = signal<string | null>(null);
}
