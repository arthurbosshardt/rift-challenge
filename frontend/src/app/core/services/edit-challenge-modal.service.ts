import { Injectable, signal } from '@angular/core';
import { ChallengeDetail } from '../models/challenge.models';

export interface EditChallengeUpdateResult {
  challenge: ChallengeDetail;
  reloadFull: boolean;
}

@Injectable({ providedIn: 'root' })
export class EditChallengeModalService {
  readonly isOpen = signal(false);
  readonly challenge = signal<ChallengeDetail | null>(null);

  private onUpdated: ((result: EditChallengeUpdateResult) => void) | null = null;

  open(challenge: ChallengeDetail, onUpdated: (result: EditChallengeUpdateResult) => void): void {
    this.challenge.set(challenge);
    this.onUpdated = onUpdated;
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.challenge.set(null);
    this.onUpdated = null;
  }

  notifyUpdated(challenge: ChallengeDetail, reloadFull = true): void {
    this.onUpdated?.({ challenge, reloadFull });
  }
}
