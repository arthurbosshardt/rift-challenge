import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { ActivityAccount } from '../../../core/services/activity-cache.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { formatRankLabel, tierColor } from '../../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../../core/utils/record-display';
import { championSplashUrl } from '../../../core/utils/champion-splash';
import { MatchHistoryStripComponent } from '../match-history-strip/match-history-strip.component';
import { ChampionPoolComponent } from '../champion-pool/champion-pool.component';
import { ChampionPoolSkeletonComponent } from '../champion-pool-skeleton/champion-pool-skeleton.component';
import { LeaderboardCategoryIconComponent } from '../leaderboard-category-icon/leaderboard-category-icon.component';

@Component({
  selector: 'app-activity-account-card',
  imports: [
    MatchHistoryStripComponent,
    ChampionPoolComponent,
    ChampionPoolSkeletonComponent,
    LeaderboardCategoryIconComponent,
    TranslatePipe,
  ],
  templateUrl: './activity-account-card.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './activity-account-card.component.scss',
})
export class ActivityAccountCardComponent {
  readonly account = input.required<ActivityAccount>();
  readonly matchClick = output<string>();

  private readonly i18n = inject(I18nService);

  protected readonly selectedView = signal<'champions' | 'performance'>('performance');

  protected setSelectedView(view: 'champions' | 'performance'): void {
    this.selectedView.set(view);
  }

  protected readonly performanceBackgroundUrl = computed(() => {
    const champions = this.account().champions ?? [];
    const mostPlayed = champions
      .filter((entry) => entry.championId != null && entry.championId > 0)
      .reduce<(typeof champions)[number] | null>(
        (best, entry) => (best == null || entry.games > best.games ? entry : best),
        null,
      );
    return championSplashUrl(mostPlayed?.championId ?? null);
  });

  protected rankLabel(): string {
    const account = this.account();
    return formatRankLabel(account.tier, account.rank, account.leaguePoints ?? 0, this.i18n.locale());
  }

  protected rankColor(): string {
    return tierColor(this.account().tier);
  }

  protected hasRecord(): boolean {
    const account = this.account();
    return hasPlayedRecord(account.wins ?? 0, account.losses ?? 0);
  }

  protected winRateLabelText(): string {
    const account = this.account();
    return winRateLabel(this.winRate(), account.wins ?? 0, account.losses ?? 0);
  }

  protected winRateClass(): string {
    const account = this.account();
    const tone = winRateToneModifier(this.winRate(), account.wins ?? 0, account.losses ?? 0);
    return `activity-account__winrate--${tone}`;
  }

  protected syncInProgress(): boolean {
    return !this.account().seasonSyncComplete;
  }

  protected syncRemaining(): number {
    const account = this.account();
    return Math.max(0, account.seasonGames - account.syncedGames);
  }

  protected onMatchClick(matchId: string): void {
    this.matchClick.emit(matchId);
  }

  private winRate(): number {
    const account = this.account();
    const wins = account.wins ?? 0;
    const losses = account.losses ?? 0;
    const total = wins + losses;
    return total > 0 ? wins / total : 0;
  }
}
