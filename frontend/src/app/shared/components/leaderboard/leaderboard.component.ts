import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { LeaderboardApiService } from '../../../core/services/leaderboard-api.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import {
  LeaderboardCategory,
  LeaderboardEntry,
  LeaderboardResponse,
  LeaderboardTimeframe,
} from '../../../core/models/leaderboard.models';
import { ParticipantMatchHistory } from '../../../core/models/challenge.models';
import { formatChallengeDateCompact } from '../../../core/utils/challenge-date';
import { formatRankLabel } from '../../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../../core/utils/record-display';
import { copyTextToClipboard } from '../../../core/utils/clipboard';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { MatchHistoryStripComponent } from '../match-history-strip/match-history-strip.component';
import { GameDetailModalService } from '../../services/game-detail-modal.service';

/** Shown briefly before the real per-window value loads from the API. */
const MIN_GAMES_FOR_WIN_RATE_FALLBACK = 20;

@Component({
  selector: 'app-leaderboard',
  imports: [TranslatePipe, PlayerAvatarComponent, MatchHistoryStripComponent],
  templateUrl: './leaderboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './leaderboard.component.scss',
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(LeaderboardApiService);
  private readonly i18n = inject(I18nService);
  private readonly gameDetailModal = inject(GameDetailModalService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly response = signal<LeaderboardResponse | null>(null);
  protected readonly timeframe = signal<LeaderboardTimeframe>('last7Days');
  protected readonly category = signal<LeaderboardCategory>('winRate');
  protected readonly nowMs = signal(Date.now());
  protected readonly copiedPuuid = signal<string | null>(null);
  protected readonly expandedPuuid = signal<string | null>(null);

  private tickInterval: ReturnType<typeof setInterval> | null = null;
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;
  private static readonly mobileHistoryMq = '(max-width: 719px)';

  protected readonly entries = computed<LeaderboardEntry[]>(() => {
    const window = this.response()?.[this.timeframe()];
    if (!window) {
      return [];
    }
    switch (this.category()) {
      case 'winRate':
        return window.byWinRate;
      case 'winStreak':
        return window.byWinStreak;
      case 'lpGained':
        return window.byLpGained;
      case 'gamesPlayed':
        return window.byGamesPlayed;
      default:
        return window.byRank;
    }
  });

  protected readonly seasonYear = computed(() => this.response()?.seasonYear ?? new Date().getUTCFullYear());

  /* `|| fallback`, not `??`: a leaderboard snapshot cached before this field existed
     deserializes it as 0 (Jackson default for a missing int), not undefined — and 0 is
     never a real threshold, so treat it the same as "not loaded yet". */
  protected readonly winRateMinGames = computed(
    () => this.response()?.[this.timeframe()]?.winRateMinGames || MIN_GAMES_FOR_WIN_RATE_FALLBACK,
  );

  ngOnInit(): void {
    this.api.getLeaderboard().subscribe({
      next: (response) => {
        this.response.set(response);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.i18n.t('leaderboard.loadError'));
      },
    });

    this.tickInterval = setInterval(() => this.nowMs.set(Date.now()), 1000);
  }

  ngOnDestroy(): void {
    if (this.tickInterval) {
      clearInterval(this.tickInterval);
      this.tickInterval = null;
    }
    if (this.copyResetTimer) {
      clearTimeout(this.copyResetTimer);
      this.copyResetTimer = null;
    }
  }

  protected async copyRiotId(entry: LeaderboardEntry, event?: Event): Promise<void> {
    event?.stopPropagation();
    if (!(await copyTextToClipboard(entry.riotId))) {
      return;
    }
    this.copiedPuuid.set(entry.puuid);
    if (this.copyResetTimer) {
      clearTimeout(this.copyResetTimer);
    }
    this.copyResetTimer = setTimeout(() => this.copiedPuuid.set(null), 1500);
  }

  protected isExpanded(entry: LeaderboardEntry): boolean {
    return this.expandedPuuid() === entry.puuid;
  }

  protected toggleExpanded(entry: LeaderboardEntry, event: Event): void {
    if (!this.isMobileHistoryViewport()) {
      return;
    }
    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a, app-match-history-strip')) {
      return;
    }
    this.expandedPuuid.update((current) => (current === entry.puuid ? null : entry.puuid));
  }

  protected soloEntries(entry: LeaderboardEntry): ParticipantMatchHistory[] {
    return entry.recentMatches ?? [];
  }

  protected openGameDetail(matchId: string, entry: LeaderboardEntry): void {
    this.gameDetailModal.open(matchId, { type: 'leaderboard', puuid: entry.puuid });
  }

  private isMobileHistoryViewport(): boolean {
    return typeof window !== 'undefined' && window.matchMedia(LeaderboardComponent.mobileHistoryMq).matches;
  }

  protected setTimeframe(value: LeaderboardTimeframe): void {
    this.timeframe.set(value);
    this.expandedPuuid.set(null);
    if (value === 'last7Days' && this.category() === 'rank') {
      this.category.set('winRate');
    }
  }

  protected setCategory(value: LeaderboardCategory): void {
    if (value === 'rank' && this.timeframe() !== 'season') {
      return;
    }
    this.category.set(value);
    this.expandedPuuid.set(null);
  }

  protected refresh(): void {
    if (this.refreshing()) {
      return;
    }
    this.refreshing.set(true);
    this.api.refreshLeaderboard().subscribe({
      next: (response) => {
        this.response.set(response);
        this.refreshing.set(false);
      },
      error: () => {
        this.refreshing.set(false);
        this.error.set(this.i18n.t('leaderboard.loadError'));
      },
    });
  }

  protected lastUpdatedLabel(): string | null {
    const response = this.response();
    if (!response) {
      return null;
    }
    return formatChallengeDateCompact(response.generatedAt, this.i18n.locale());
  }

  protected countdownValue(): string | null {
    const response = this.response();
    if (!response) {
      return null;
    }
    const remainingMs = new Date(response.nextRefreshAt).getTime() - this.nowMs();
    if (Number.isNaN(remainingMs) || remainingMs <= 0) {
      return null;
    }
    const totalSeconds = Math.ceil(remainingMs / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    const pad = (value: number) => value.toString().padStart(2, '0');
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }

  protected rankLabel(entry: LeaderboardEntry): string {
    return formatRankLabel(entry.tier, entry.rankDivision, entry.leaguePoints, this.i18n.locale());
  }

  protected hasRecord = hasPlayedRecord;

  protected winRateLabel(entry: LeaderboardEntry): string {
    return winRateLabel(entry.winRate, entry.wins, entry.losses);
  }

  protected winRateClass(entry: LeaderboardEntry): string {
    return `leaderboard__winrate--${winRateToneModifier(entry.winRate, entry.wins, entry.losses)}`;
  }

  protected lpGainedLabel(entry: LeaderboardEntry): string {
    return entry.lpGained >= 0 ? `+${entry.lpGained} LP` : `${entry.lpGained} LP`;
  }
}
