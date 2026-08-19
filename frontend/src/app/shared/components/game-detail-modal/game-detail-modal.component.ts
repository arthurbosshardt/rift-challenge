import { Component, ChangeDetectionStrategy, computed, effect, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ChallengeApiService } from '../../../core/services/challenge-api.service';
import { GameDetailModalService } from '../../services/game-detail-modal.service';
import { MatchDetail, MatchItem, MatchParticipant } from '../../../core/models/challenge.models';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { rankEmblemUrl, formatRankLabel, tierLabel } from '../../../core/utils/rank-display';
import { GameDetailSkeletonComponent } from '../game-detail-skeleton/game-detail-skeleton.component';
import { ItemDataService } from '../../../core/services/item-data.service';
import { apiUrl } from '../../../core/utils/api-url';

const HIGH_TIERS = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER']);

const ROLE_ICON_PATHS: Record<string, string> = {
  TOP: '/roles/top.png',
  JUNGLE: '/roles/jungle.png',
  MIDDLE: '/roles/mid.png',
  BOTTOM: '/roles/bot.png',
  UTILITY: '/roles/support.png',
};

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
  private readonly itemData = inject(ItemDataService);

  protected readonly detail = signal<MatchDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedTeam = signal<'mine' | 'enemy'>('mine');
  protected readonly copiedKey = signal<string | null>(null);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly hoveredItem = signal<{
    itemId: number;
    iconUrl: string;
    top: number;
    left: number;
    placement: 'above' | 'below';
  } | null>(null);

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

  protected roleIconUrl(role: string | null): string | null {
    if (!role) {
      return null;
    }
    return ROLE_ICON_PATHS[role] ?? null;
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

  protected rankTierName(player: MatchParticipant): string {
    return tierLabel(player.rankTier, this.i18n.locale());
  }

  protected rankTierWithDivision(player: MatchParticipant): string {
    const tier = this.rankTierName(player);
    if (!player.rankTier || !player.rankDivision || HIGH_TIERS.has(player.rankTier.toUpperCase())) {
      return tier;
    }
    return `${tier} ${player.rankDivision}`;
  }

  protected rankLpOnly(player: MatchParticipant): string {
    return `${player.rankLeaguePoints ?? 0} LP`;
  }

  protected rankTitle(player: MatchParticipant): string {
    return formatRankLabel(player.rankTier, player.rankDivision, player.rankLeaguePoints ?? 0, this.i18n.locale());
  }

  protected championIconSrc(url: string | null): string | null {
    return url ? apiUrl(url) : null;
  }

  protected itemDetails(itemId: number | null): { name: string; description: string } | null {
    return this.itemData.details(itemId);
  }

  protected onItemHoverStart(event: MouseEvent | FocusEvent, item: MatchItem): void {
    if (!item.itemId || !item.iconUrl) {
      return;
    }
    const target = event.currentTarget as HTMLElement;
    const rect = target.getBoundingClientRect();
    const tooltipHalfWidth = 128;
    const estimatedTooltipHeight = 160;
    const placement: 'above' | 'below' = rect.top > estimatedTooltipHeight ? 'above' : 'below';
    const left = Math.min(
      Math.max(rect.left + rect.width / 2, tooltipHalfWidth + 8),
      window.innerWidth - tooltipHalfWidth - 8,
    );
    this.hoveredItem.set({
      itemId: item.itemId,
      iconUrl: item.iconUrl,
      top: placement === 'above' ? rect.top - 8 : rect.bottom + 8,
      left,
      placement,
    });
  }

  protected onItemHoverEnd(): void {
    this.hoveredItem.set(null);
  }

  protected durationAria(seconds: number): string {
    return this.i18n.t('gameDetail.durationAria', { duration: this.formatDuration(seconds) });
  }

  protected matchDateLabel(playedAt: string): string {
    const date = new Date(playedAt);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const day = date.getDate().toString().padStart(2, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    return this.i18n.locale() === 'en' ? `${month}/${day}` : `${day}/${month}`;
  }

  private playerKey(player: MatchParticipant): string {
    return `${player.gameName}#${player.tagLine}`;
  }

  protected isCopied(player: MatchParticipant): boolean {
    return this.copiedKey() === this.playerKey(player);
  }

  protected copyNameAria(player: MatchParticipant): string {
    return this.i18n.t('player.copyRiotIdAria', { riotId: this.playerKey(player) });
  }

  protected copyName(player: MatchParticipant, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    void this.performCopy(player);
  }

  private async performCopy(player: MatchParticipant): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.playerKey(player));
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
