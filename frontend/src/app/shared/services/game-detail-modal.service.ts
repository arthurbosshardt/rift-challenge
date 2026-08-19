import { Injectable, signal } from '@angular/core';

export type GameDetailModalContext =
  | { type: 'account'; accountId: string }
  | { type: 'participant'; challengeId: string; participantId: string }
  | { type: 'duo'; challengeId: string; duoId: string };

@Injectable({ providedIn: 'root' })
export class GameDetailModalService {
  readonly isOpen = signal(false);
  readonly matchId = signal<string | null>(null);
  readonly context = signal<GameDetailModalContext | null>(null);

  open(matchId: string, context: GameDetailModalContext): void {
    this.matchId.set(matchId);
    this.context.set(context);
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.matchId.set(null);
    this.context.set(null);
  }
}
