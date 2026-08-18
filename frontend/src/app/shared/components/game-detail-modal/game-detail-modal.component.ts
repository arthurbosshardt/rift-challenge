import { Component, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { ChampionIconComponent } from '../champion-icon/champion-icon.component';
import { I18nService } from '../../core/i18n/i18n.service';

export interface GameDetail {
  id: string;
  gameName: string;
  tagLine: string;
  championId: number | null;
  championIconUrl: string | null;
  win: boolean;
  playedAt: string;
}

@Component({
  selector: 'app-game-detail-modal',
  imports: [CommonModule, TranslatePipe, ChampionIconComponent],
  templateUrl: './game-detail-modal.component.html',
  styleUrl: './game-detail-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameDetailModalComponent {
  readonly game = input.required<GameDetail>();
  private readonly i18n = inject(I18nService);

  protected formatPlayedAt(playedAt: string): string {
    const date = new Date(playedAt);
    return date.toLocaleString(this.i18n.locale());
  }

  protected formatPlayedAtRelative(playedAt: string): string {
    const date = new Date(playedAt);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) {
      return this.i18n.locale() === 'fr' ? 'à l\'instant' : 'just now';
    }

    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return this.i18n.locale() === 'fr'
        ? `il y a ${minutes} minute${minutes > 1 ? 's' : ''}`
        : `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
    }

    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return this.i18n.locale() === 'fr'
        ? `il y a ${hours} heure${hours > 1 ? 's' : ''}`
        : `${hours} hour${hours > 1 ? 's' : ''} ago`;
    }

    const days = Math.floor(hours / 24);
    return this.i18n.locale() === 'fr'
      ? `il y a ${days} jour${days > 1 ? 's' : ''}`
      : `${days} day${days > 1 ? 's' : ''} ago`;
  }
}
