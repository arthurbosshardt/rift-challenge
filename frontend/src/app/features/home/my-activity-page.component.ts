import { Component, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { ActivityCacheService } from '../../core/services/activity-cache.service';
import { AccountRecentGames } from '../../core/models/challenge.models';
import { formatRankLabel, tierColor } from '../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../core/utils/record-display';
import { formatTimeSince } from '../../core/utils/relative-time';
import { RefreshCooldown } from '../../core/utils/refresh-cooldown';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { MatchHistoryStripComponent } from '../../shared/components/match-history-strip/match-history-strip.component';
import { MatchHistorySkeletonComponent } from '../../shared/components/match-history-skeleton/match-history-skeleton.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { GameDetailModalService } from '../../shared/services/game-detail-modal.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

type ActivityView = 'activity' | 'challenges';

@Component({
  selector: 'app-my-activity-page',
  imports: [
    PageShellComponent,
    ChallengeCardComponent,
    ChallengeListSkeletonComponent,
    PlayerIdentityComponent,
    MatchHistoryStripComponent,
    MatchHistorySkeletonComponent,
    SkeletonComponent,
    NavIconComponent,
    TranslatePipe,
  ],
  templateUrl: './my-activity-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './my-activity-page.component.scss',
})
export class MyActivityPageComponent implements OnInit, OnDestroy {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly gameDetailModal = inject(GameDetailModalService);
  private readonly i18n = inject(I18nService);
  private readonly cache = inject(ActivityCacheService);

  protected readonly view = signal<ActivityView>('activity');

  protected readonly challenges = this.cache.challenges;
  protected readonly challengesLoading = signal(this.cache.challengesLastLoadedAt() === null);
  protected readonly challengesError = signal<string | null>(null);
  private readonly challengesCooldown = new RefreshCooldown();
  protected readonly challengesRefreshCooldownSeconds = this.challengesCooldown.seconds;

  protected readonly activityAccounts = this.cache.activityAccounts;
  protected readonly activityLoading = signal(this.cache.activityLastLoadedAt() === null);
  protected readonly activityError = signal<string | null>(null);
  private readonly activityCooldown = new RefreshCooldown();
  protected readonly refreshCooldownSeconds = this.activityCooldown.seconds;
  protected readonly lastRefreshedAt = this.cache.lastRefreshedAt;

  protected readonly activitySkeletonRows = [0, 1];

  ngOnInit(): void {
    void this.loadPage();
  }

  ngOnDestroy(): void {
    this.activityCooldown.clear();
    this.challengesCooldown.clear();
  }

  protected setView(view: ActivityView): void {
    this.view.set(view);
  }

  protected openGameDetail(matchId: string, accountId: string): void {
    this.gameDetailModal.open(matchId, { type: 'account', accountId });
  }

  protected accountRankLabel(account: AccountRecentGames): string {
    return formatRankLabel(account.tier, account.rank, account.leaguePoints ?? 0, this.i18n.locale());
  }

  protected accountRankColor(account: AccountRecentGames): string {
    return tierColor(account.tier);
  }

  protected accountHasRecord(account: AccountRecentGames): boolean {
    return hasPlayedRecord(account.wins ?? 0, account.losses ?? 0);
  }

  protected accountWinRateLabel(account: AccountRecentGames): string {
    return winRateLabel(this.accountWinRate(account), account.wins ?? 0, account.losses ?? 0);
  }

  protected accountWinRateClass(account: AccountRecentGames): string {
    const tone = winRateToneModifier(this.accountWinRate(account), account.wins ?? 0, account.losses ?? 0);
    return `activity-account__winrate--${tone}`;
  }

  private accountWinRate(account: AccountRecentGames): number {
    const wins = account.wins ?? 0;
    const losses = account.losses ?? 0;
    const total = wins + losses;
    return total > 0 ? wins / total : 0;
  }

  protected lastRefreshedLabel(): string | null {
    const lastRefreshedAt = this.lastRefreshedAt();
    if (!lastRefreshedAt) {
      return null;
    }
    const time = formatTimeSince(lastRefreshedAt, Date.now(), this.i18n.locale());
    if (!time) {
      return null;
    }
    return this.i18n.t('activity.lastRefreshed', { time });
  }

  protected refreshButtonLabel(): string {
    return this.cooldownLabel(this.refreshCooldownSeconds(), 'activity.refreshLabel');
  }

  protected challengesRefreshButtonLabel(): string {
    return this.cooldownLabel(this.challengesRefreshCooldownSeconds(), 'home.searchRefreshLabel');
  }

  private cooldownLabel(seconds: number, idleKey: string): string {
    if (seconds > 0) {
      return this.i18n.locale() === 'en' ? `${seconds}seconds` : `${seconds} secondes`;
    }
    return this.i18n.t(idleKey);
  }

  protected refreshActivity(): void {
    if (this.activityCooldown.active || this.activityLoading()) {
      return;
    }
    this.loadActivity();
    this.activityCooldown.start();
  }

  protected refreshMyChallenges(): void {
    if (this.challengesCooldown.active || this.challengesLoading()) {
      return;
    }
    this.loadChallenges();
    this.challengesCooldown.start();
  }

  private async loadPage(): Promise<void> {
    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      const message = this.i18n.t('home.sessionExpired');
      this.challengesError.set(message);
      this.activityError.set(message);
      this.challengesLoading.set(false);
      this.activityLoading.set(false);
      return;
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    if (!this.auth.linkedAccount()) {
      this.challengesLoading.set(false);
      this.activityLoading.set(false);
      return;
    }

    if (this.cache.challengesLastLoadedAt() === null) {
      void this.loadChallenges();
    } else {
      this.challengesLoading.set(false);
    }

    if (this.cache.activityLastLoadedAt() === null) {
      void this.loadActivity();
    } else {
      this.activityLoading.set(false);
    }
  }

  private loadChallenges(): void {
    this.challengesLoading.set(true);
    this.challengeApi.listParticipatingChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.cache.challengesLastLoadedAt.set(Date.now());
        this.challengesLoading.set(false);
      },
      error: (err: { status?: number }) => {
        this.challengesError.set(
          err.status === 401 ? this.i18n.t('home.sessionExpired') : this.i18n.t('home.loadParticipatingError'),
        );
        this.challengesLoading.set(false);
      },
    });
  }

  private loadActivity(): void {
    this.activityLoading.set(true);
    this.challengeApi.listRecentGames().subscribe({
      next: (accounts) => {
        this.activityAccounts.set(
          accounts.map((account) => ({
            ...account,
            matches: account.games.map((game) => ({
              matchId: game.id,
              championId: game.championId,
              championIconUrl: game.championIconUrl,
              win: game.win,
              lpDelta: 0,
              playedAt: game.playedAt,
            })),
          })),
        );
        this.cache.activityLastLoadedAt.set(Date.now());
        this.activityLoading.set(false);
        this.lastRefreshedAt.set(new Date().toISOString());
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.activityError.set(this.i18n.t('home.sessionExpired'));
        } else if (err.status === 429) {
          this.activityError.set(this.i18n.t('errors.riotRateLimit'));
        } else {
          this.activityError.set(this.i18n.t('activity.loadError'));
        }
        this.activityLoading.set(false);
      },
    });
  }
}
