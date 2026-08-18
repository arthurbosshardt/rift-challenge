import { Component, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { ChampionIconComponent } from '../champion-icon/champion-icon.component';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { I18nService } from '../../../core/i18n/i18n.service';
import { GameDetailModalService } from '../../services/game-detail-modal.service';
import { GameDetail } from '../../../core/models/challenge.models';

@Component({
  selector: 'app-game-detail-modal',
  imports: [CommonModule, TranslatePipe, ChampionIconComponent, PlayerAvatarComponent],
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
      return this.i18n.t('game.minutesAgo', {
        count: minutes,
        countPlural: minutes > 1 ? 's' : '',
      });
    }

    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return this.i18n.t('game.hoursAgo', {
        count: hours,
        countPlural: hours > 1 ? 's' : '',
      });
    }

    const days = Math.floor(hours / 24);
    return this.i18n.t('game.daysAgo', {
      count: days,
      countPlural: days > 1 ? 's' : '',
    });
  }

  protected formatDuration(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
  }

  protected formatGold(gold: number): string {
    return this.formatLargeNumber(gold);
  }

  protected formatDamage(damage?: number): string {
    if (damage === undefined || damage === null) return '-';
    return this.formatLargeNumber(damage);
  }

  private formatLargeNumber(value: number): string {
    if (value >= 1000000) {
      return `${(value / 1000000).toFixed(1)}M`;
    }
    if (value >= 1000) {
      return `${(value / 1000).toFixed(1)}K`;
    }
    return value.toString();
  }
}
