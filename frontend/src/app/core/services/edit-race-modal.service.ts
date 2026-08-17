import { Injectable, signal } from '@angular/core';
import { RaceDetail } from '../models/race.models';

@Injectable({ providedIn: 'root' })
export class EditRaceModalService {
  readonly isOpen = signal(false);
  readonly race = signal<RaceDetail | null>(null);

  private onUpdated: (() => void) | null = null;

  open(race: RaceDetail, onUpdated: () => void): void {
    this.race.set(race);
    this.onUpdated = onUpdated;
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.race.set(null);
    this.onUpdated = null;
  }

  notifyUpdated(): void {
    this.onUpdated?.();
  }
}
