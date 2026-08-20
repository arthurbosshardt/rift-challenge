import { Injectable, signal } from '@angular/core';
import { ChallengeSummary } from '../models/challenge.models';

@Injectable({ providedIn: 'root' })
export class PublicChallengesCacheService {
  readonly challenges = signal<ChallengeSummary[]>([]);
  readonly lastLoadedAt = signal<number | null>(null);
  readonly generatedAt = signal<string | null>(null);
  readonly refreshAvailable = signal(true);
  readonly nextRefreshAvailableAt = signal<string | null>(null);
}
