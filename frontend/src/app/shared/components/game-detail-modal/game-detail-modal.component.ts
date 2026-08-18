import { Component, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { ChampionIconComponent } from '../champion-icon/champion-icon.component';
import { I18nService } from '../../core/i18n/i18n.service';
import { GameDetailModalService } from '../../services/game-detail-modal.service';
import { GameDetail } from '../../core/models/challenge.models';

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
  protected readonly modalService = inject(GameDetailModalService);

  protected formatPlayedAt(playedAt: string): string {
    const date = new Date(playedAt);
    return date.toLocaleString(this.i18n.locale());
  }

  protected formatPlayedAtRelative(playedAt: string): string {
    const date = new Date(playedAt);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) {
      return this.i18n.t('game.justNow');
    }

    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      const minuteStr = minutes === 1 ? 'minute' : 'minutes';
      return this.i18n.locale() === 'fr'
        ? `il y a ${minutes} ${minuteStr}`
        : `${minutes} ${minuteStr} ago`;
    }

    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      const hourStr = hours === 1 ? 'heure' : 'heures';
      return this.i18n.locale() === 'fr'
        ? `il y a ${hours} ${hourStr}`
        : `${hours} ${hourStr === 'heure' ? 'hour' : 'hours'} ago`;
    }

    const days = Math.floor(hours / 24);
    const dayStr = days === 1 ? 'jour' : 'jours';
    return this.i18n.locale() === 'fr'
      ? `il y a ${days} ${dayStr}`
      : `${days} ${dayStr === 'jour' ? 'day' : 'days'} ago`;
  }
}
