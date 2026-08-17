import { Injectable, signal } from '@angular/core';
import { ChallengeDetail } from '../models/challenge.models';

@Injectable({ providedIn: 'root' })
export class EditChallengeModalService {
  readonly isOpen = signal(false);
  readonly challenge = signal<ChallengeDetail | null>(null);

  private onUpdated: (() => void) | null = null;

  open(challenge: ChallengeDetail, onUpdated: () => void): void {
    this.challenge.set(challenge);
    this.onUpdated = onUpdated;
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.challenge.set(null);
    this.onUpdated = null;
  }

  notifyUpdated(): void {
    this.onUpdated?.();
  }
}
