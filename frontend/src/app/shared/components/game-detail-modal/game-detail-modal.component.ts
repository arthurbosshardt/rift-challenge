import { Component, ChangeDetectionStrategy, computed, effect, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ChallengeApiService } from '../../../core/services/challenge-api.service';
import { GameDetailModalService } from '../../services/game-detail-modal.service';
import { MatchDetail, MatchParticipant } from '../../../core/models/challenge.models';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { rankEmblemUrl, formatRankLabel } from '../../../core/utils/rank-display';
import { GameDetailSkeletonComponent } from '../game-detail-skeleton/game-detail-skeleton.component';

const HIGH_TIERS = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER']);

@Component({
  selector: 'app-game-detail-modal',
  imports: [NgTemplateOutlet, TranslatePipe, GameDetailSkeletonComponent],
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
  protected readonly selectedTeam = signal<'mine' | 'enemy'>('mine');
  protected readonly copiedKey = signal<string | null>(null);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly rows = computed(() => {
    const match = this.detail();
    if (!match) {
      return [];
    }
    const length = Math.max(match.myTeam.length, match.enemyTeam.length);
    return Array.from({ length }, (_, index) => ({
      mine: match.myTeam[index] ?? null,
      enemy: match.enemyTeam[index] ?? null,
    }));
  });

  constructor() {
    effect(() => {
      if (this.modalService.isOpen()) {
        document.body.style.overflow = 'hidden';
        this.selectedTeam.set('mine');
        this.load();
        return;
      }

      document.body.style.overflow = '';
      this.detail.set(null);
      this.error.set(null);
    });
  }

  protected setSelectedTeam(team: 'mine' | 'enemy'): void {
    this.selectedTeam.set(team);
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

  protected csPerMinute(player: MatchParticipant): string {
    const match = this.detail();
    if (!match || match.durationSeconds <= 0) {
      return '0.0';
    }
    return (player.cs / (match.durationSeconds / 60)).toFixed(1);
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

  protected teamSide(team: MatchParticipant[]): 'blue' | 'red' {
    return team[0]?.teamId === 200 ? 'red' : 'blue';
  }

  protected damageBarWidth(player: MatchParticipant): number {
    const match = this.detail();
    if (!match) {
      return 0;
    }
    const maxDamage = Math.max(1, ...match.myTeam.map((p) => p.damageDealt), ...match.enemyTeam.map((p) => p.damageDealt));
    return Math.max(4, Math.round((player.damageDealt / maxDamage) * 100));
  }

  protected rankEmblemUrl(player: MatchParticipant): string | null {
    return rankEmblemUrl(player.rankTier);
  }

  protected rankShortLabel(player: MatchParticipant): string {
    if (!player.rankTier) {
      return '';
    }
    if (HIGH_TIERS.has(player.rankTier.toUpperCase())) {
      return `${player.rankLeaguePoints ?? 0}`;
    }
    return player.rankDivision ?? '';
  }

  protected rankTitle(player: MatchParticipant): string {
    return formatRankLabel(player.rankTier, player.rankDivision, player.rankLeaguePoints ?? 0, this.i18n.locale());
  }

  private playerKey(player: MatchParticipant): string {
    return `${player.gameName}#${player.tagLine}`;
  }

  protected isCopied(player: MatchParticipant): boolean {
    return this.copiedKey() === this.playerKey(player);
  }

  protected copyNameAria(player: MatchParticipant): string {
    return this.i18n.t('player.copyNameAria', { name: player.gameName });
  }

  protected copyName(player: MatchParticipant, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    void this.performCopy(player);
  }

  private async performCopy(player: MatchParticipant): Promise<void> {
    try {
      await navigator.clipboard.writeText(player.gameName);
      this.copiedKey.set(this.playerKey(player));
      if (this.copyResetTimer) {
        clearTimeout(this.copyResetTimer);
      }
      this.copyResetTimer = setTimeout(() => this.copiedKey.set(null), 1500);
    } catch {
      /* clipboard unavailable */
    }
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
