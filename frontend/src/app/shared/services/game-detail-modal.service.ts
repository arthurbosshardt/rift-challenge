import { Injectable, signal, Signal } from '@angular/core';
import type { GameDetail } from '../components/game-detail-modal/game-detail-modal.component';

@Injectable({
  providedIn: 'root',
})
export class GameDetailModalService {
  private readonly _isOpen = signal(false);
  private readonly _game = signal<GameDetail | null>(null);

  readonly isOpen: Signal<boolean> = this._isOpen.asReadonly();
  readonly game: Signal<GameDetail | null> = this._game.asReadonly();

  open(game: GameDetail): void {
    this._game.set(game);
    this._isOpen.set(true);
  }

  close(): void {
    this._isOpen.set(false);
    this._game.set(null);
  }
}
