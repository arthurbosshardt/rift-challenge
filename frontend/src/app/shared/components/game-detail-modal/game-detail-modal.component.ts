import { Component, ChangeDetectionStrategy, effect, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ChallengeApiService } from '../../../core/services/challenge-api.service';
import { GameDetailModalService } from '../../services/game-detail-modal.service';
import { MatchDetail, MatchParticipant } from '../../../core/models/challenge.models';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-game-detail-modal',
  imports: [NgTemplateOutlet, TranslatePipe],
  templateUrl: './game-detail-modal.component.html',
  styleUrl: './game-detail-modal.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class GameDetailModalComponent {
  protected readonly modalService = inject(GameDetailModalService);
  private readonly challengeApi = inject(ChallengeApiService);
  private readonly i18n = inject(I18nService);

  protected readonly detail = signal<MatchDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.modalService.isOpen()) {
        document.body.style.overflow = 'hidden';
        this.load();
        return;
      }

      document.body.style.overflow = '';
      this.detail.set(null);
      this.error.set(null);
    });
  }

  protected close(): void {
    this.modalService.close();
  }

  protected formatDuration(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
  }

  protected formatNumber(value: number): string {
    if (value >= 1000) {
      return `${(value / 1000).toFixed(1)}k`;
    }
    return `${value}`;
  }

  protected kda(player: MatchParticipant): string {
    return `${player.kills}/${player.deaths}/${player.assists}`;
  }

  protected kdaRatio(player: MatchParticipant): string {
    const ratio = player.deaths === 0 ? player.kills + player.assists : (player.kills + player.assists) / player.deaths;
    return `${ratio.toFixed(1)}:1`;
  }

  protected roleLabel(role: string | null): string {
    if (!role) {
      return '';
    }
    return this.i18n.t(`gameDetail.role.${role}`);
  }

  protected teamKills(team: MatchParticipant[]): number {
    return team.reduce((sum, player) => sum + player.kills, 0);
  }

  protected teamGold(team: MatchParticipant[]): number {
    return team.reduce((sum, player) => sum + player.goldEarned, 0);
  }

  protected damageBarWidth(player: MatchParticipant): number {
    const match = this.detail();
    if (!match) {
      return 0;
    }
    const maxDamage = Math.max(1, ...match.myTeam.map((p) => p.damageDealt), ...match.enemyTeam.map((p) => p.damageDealt));
    return Math.max(4, Math.round((player.damageDealt / maxDamage) * 100));
  }

  private load(): void {
    const matchId = this.modalService.matchId();
    const context = this.modalService.context();
    if (!matchId || !context) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.detail.set(null);

    const request$ =
      context.type === 'account'
        ? this.challengeApi.getAccountMatchDetail(context.accountId, matchId)
        : context.type === 'participant'
          ? this.challengeApi.getParticipantMatchDetail(context.challengeId, context.participantId, matchId)
          : this.challengeApi.getDuoMatchDetail(context.challengeId, context.duoId, matchId);

    request$.subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.i18n.t('gameDetail.loadError'));
        this.loading.set(false);
      },
    });
  }
}
