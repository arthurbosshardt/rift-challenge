import { Component, effect, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { ActivityCacheService, ActivityAccount, normalizeActivityAccount } from '../../core/services/activity-cache.service';
import { PublicChallengesCacheService } from '../../core/services/public-challenges-cache.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { AccountRecentGames, ChallengeListResponse } from '../../core/models/challenge.models';
import { formatRankLabel, tierColor } from '../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../core/utils/record-display';
import { formatTimeSince } from '../../core/utils/relative-time';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { RefreshCooldown } from '../../core/utils/refresh-cooldown';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { MatchHistoryStripComponent } from '../../shared/components/match-history-strip/match-history-strip.component';
import { MatchHistorySkeletonComponent } from '../../shared/components/match-history-skeleton/match-history-skeleton.component';
import { ChampionPoolComponent } from '../../shared/components/champion-pool/champion-pool.component';
import { ChampionPoolSkeletonComponent } from '../../shared/components/champion-pool-skeleton/champion-pool-skeleton.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { GameDetailModalService } from '../../shared/services/game-detail-modal.service';
import { LeaderboardCategoryIconComponent } from '../../shared/components/leaderboard-category-icon/leaderboard-category-icon.component';

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
    ChampionPoolComponent,
    ChampionPoolSkeletonComponent,
    SkeletonComponent,
    NavIconComponent,
    TranslatePipe,
    LeaderboardCategoryIconComponent,
  ],
  templateUrl: './my-activity-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './my-activity-page.component.scss',
})
export class MyActivityPageComponent implements OnInit, OnDestroy {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly gameDetailModal = inject(GameDetailModalService);
  private readonly i18n = inject(I18nService);
  private readonly cache = inject(ActivityCacheService);
  private readonly publicCache = inject(PublicChallengesCacheService);
  protected readonly backend = inject(BackendStatusService);

  protected readonly view = signal<ActivityView>('activity');

  protected readonly challenges = this.cache.challenges;
  protected readonly challengesLoading = signal(this.cache.challengesLastLoadedAt() === null);
  protected readonly challengesRefreshing = signal(false);
  protected readonly challengesRefreshAvailable = this.publicCache.refreshAvailable;
  protected readonly challengesError = signal<string | null>(null);
  private challengesTickInterval: ReturnType<typeof setInterval> | null = null;
  protected readonly challengesNowMs = signal(Date.now());

  protected readonly activityAccounts = this.cache.activityAccounts;
  protected readonly activityLoading = signal(this.cache.activityLastLoadedAt() === null);
  protected readonly activityError = signal<string | null>(null);
  private readonly activityCooldown = new RefreshCooldown();
  protected readonly refreshCooldownSeconds = this.activityCooldown.seconds;
  protected readonly lastRefreshedAt = this.cache.lastRefreshedAt;
  private seasonSyncPollInterval: ReturnType<typeof setInterval> | null = null;

  protected readonly activitySkeletonRows = [0, 1];

  private linkedAccountTracker: string | null | undefined;

  constructor() {
    effect(() => {
      const linkedAccountId = this.auth.linkedAccount()?.id ?? null;
      if (this.linkedAccountTracker === undefined) {
        this.linkedAccountTracker = linkedAccountId;
        return;
      }
      if (linkedAccountId === this.linkedAccountTracker) {
        return;
      }
      this.linkedAccountTracker = linkedAccountId;
      void this.onLinkedAccountChanged(linkedAccountId);
    });
  }

  ngOnInit(): void {
    void this.loadPage();
  }

  ngOnDestroy(): void {
    this.activityCooldown.clear();
    this.stopChallengesCountdownTick();
    this.stopSeasonSyncPolling();
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

  protected accountSeasonSyncInProgress(account: ActivityAccount): boolean {
    return !account.seasonSyncComplete;
  }

  private hasIncompleteSeasonSync(): boolean {
    return this.activityAccounts().some((account) => !account.seasonSyncComplete);
  }

  /** Cache from before champion stats could omit `champions` while games are already stored. */
  private hasStaleActivityChampions(): boolean {
    return this.activityAccounts().some(
      (account) =>
        (account.matches.length > 0 || account.syncedGames > 0) &&
        (account.champions ?? []).length === 0,
    );
  }

  protected accountSyncRemaining(account: ActivityAccount): number {
    return Math.max(0, account.seasonGames - account.syncedGames);
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

  protected challengesLastUpdatedLabel(): string | null {
    const generatedAt = this.cache.challengesGeneratedAt();
    if (!generatedAt) {
      return null;
    }
    const time = formatTimeSince(generatedAt, Date.now(), this.i18n.locale());
    if (!time) {
      return null;
    }
    return this.i18n.t('activity.lastRefreshed', { time });
  }

  private cooldownLabel(seconds: number, idleKey: string): string {
    if (seconds > 0) {
      return this.i18n.locale() === 'en' ? `${seconds}seconds` : `${seconds} secondes`;
    }
    return this.i18n.t(idleKey);
  }

  protected activityRefreshDisabled(): boolean {
    return (
      this.activityLoading() ||
      this.refreshCooldownSeconds() > 0 ||
      !this.backend.ready() ||
      this.hasIncompleteSeasonSync()
    );
  }

  protected refreshActivity(): void {
    if (this.activityCooldown.active || this.activityLoading() || this.hasIncompleteSeasonSync()) {
      return;
    }
    this.activityError.set(null);
    this.loadActivity(false, true);
    this.activityCooldown.start();
  }

  protected challengesRefreshButtonLabel(): string {
    const nextAvailable = this.publicCache.nextRefreshAvailableAt();
    if (!this.challengesRefreshAvailable() && nextAvailable) {
      const countdown = formatRefreshCountdown(nextAvailable, this.challengesNowMs());
      if (countdown) {
        return countdown;
      }
    }
    return this.i18n.t('home.searchRefreshLabel');
  }

  protected refreshChallenges(): void {
    if (!this.challengesRefreshAvailable() || this.challengesRefreshing() || this.challengesLoading()) {
      return;
    }

    this.challengesRefreshing.set(true);
    this.challengesError.set(null);

    this.challengeApi.refreshPublicChallenges().subscribe({
      next: () => this.loadChallenges(true),
      error: (err: { status?: number }) => {
        this.challengesError.set(
          err.status === 429 ? this.i18n.t('home.refreshOnCooldown') : this.i18n.t('home.loadParticipatingError'),
        );
        this.challengesRefreshing.set(false);
      },
    });
  }

  private startChallengesCountdownTick(): void {
    this.stopChallengesCountdownTick();
    this.challengesTickInterval = setInterval(() => {
      this.challengesNowMs.set(Date.now());
      const nextAvailable = this.publicCache.nextRefreshAvailableAt();
      if (
        this.challengesRefreshAvailable() ||
        !nextAvailable ||
        !formatRefreshCountdown(nextAvailable, this.challengesNowMs())
      ) {
        this.publicCache.refreshAvailable.set(true);
        this.stopChallengesCountdownTick();
      }
    }, 1000);
  }

  private stopChallengesCountdownTick(): void {
    if (this.challengesTickInterval) {
      clearInterval(this.challengesTickInterval);
      this.challengesTickInterval = null;
    }
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

    const ownerKey = this.auth.userId();
    if (ownerKey) {
      this.cache.hydrateForOwner(ownerKey);
      this.challengesLoading.set(this.cache.challengesLastLoadedAt() === null);
      this.activityLoading.set(this.cache.activityLastLoadedAt() === null);
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
      if (!this.challengesRefreshAvailable()) {
        this.startChallengesCountdownTick();
      }
    }

    if (this.cache.activityLastLoadedAt() === null) {
      void this.loadActivity();
    } else if (this.hasIncompleteSeasonSync() || this.hasStaleActivityChampions()) {
      this.activityLoading.set(false);
      void this.loadActivity(true);
      this.updateSeasonSyncPolling();
    } else {
      this.activityLoading.set(false);
    }
  }

  private loadChallenges(isRefresh = false): void {
    if (isRefresh) {
      this.challengesRefreshing.set(true);
    } else {
      this.challengesLoading.set(true);
    }
    this.challengeApi.listParticipatingChallenges().subscribe({
      next: (response: ChallengeListResponse) => {
        this.cache.setChallenges(response.challenges, response.generatedAt);
        this.publicCache.refreshAvailable.set(response.refreshAvailable);
        this.publicCache.nextRefreshAvailableAt.set(response.nextRefreshAvailableAt);
        this.challengesLoading.set(false);
        this.challengesRefreshing.set(false);
        if (response.refreshAvailable) {
          this.stopChallengesCountdownTick();
        } else {
          this.startChallengesCountdownTick();
        }
      },
      error: (err: { status?: number }) => {
        this.challengesLoading.set(false);
        this.challengesRefreshing.set(false);
        if (err.status === 401) {
          this.challengesError.set(this.i18n.t('home.sessionExpired'));
        } else if (this.backend.ready()) {
          this.challengesError.set(this.i18n.t('home.loadParticipatingError'));
        } else {
          this.challengesError.set(this.i18n.t('backend.wakingUpMessage'));
          this.backend.onReady(() => this.loadChallenges());
        }
      },
    });
  }

  private async onLinkedAccountChanged(linkedAccountId: string | null): Promise<void> {
    this.activityError.set(null);
    this.activityCooldown.clear();
    this.stopSeasonSyncPolling();
    this.cache.invalidateActivity();

    if (!linkedAccountId) {
      this.activityLoading.set(false);
      return;
    }

    await this.auth.waitUntilReady();
    if (!(await this.auth.resolveAccessToken())) {
      this.activityLoading.set(false);
      return;
    }

    this.loadActivity(false, true);
  }

  private loadActivity(silent = false, refresh = false): void {
    if (!silent) {
      this.activityLoading.set(true);
    }
    this.challengeApi.listRecentGames({ refresh }).subscribe({
      next: (accounts) => {
        this.activityError.set(null);
        this.cache.setActivity(
          accounts.map((account) => normalizeActivityAccount(account)),
          new Date().toISOString(),
          !silent,
        );
        this.activityLoading.set(false);
        this.updateSeasonSyncPolling();
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        if (!silent) {
          this.activityLoading.set(false);
        }
        if (silent) {
          return;
        }
        if (err.status === 401) {
          this.activityError.set(this.i18n.t('home.sessionExpired'));
        } else if (err.status === 429) {
          const message = err.error?.message?.toLowerCase() ?? '';
          if (message.includes('wait before refreshing')) {
            this.activityCooldown.start();
          } else {
            this.activityError.set(this.i18n.t('errors.riotRateLimit'));
          }
        } else if (this.backend.ready()) {
          this.activityError.set(this.i18n.t('activity.loadError'));
        } else {
          this.activityError.set(this.i18n.t('backend.wakingUpMessage'));
          this.backend.onReady(() => this.loadActivity());
        }
      },
    });
  }

  private updateSeasonSyncPolling(): void {
    const needsPoll = this.hasIncompleteSeasonSync();
    if (needsPoll && this.seasonSyncPollInterval === null) {
      this.seasonSyncPollInterval = setInterval(() => this.loadActivity(true), 45_000);
    } else if (!needsPoll) {
      this.stopSeasonSyncPolling();
    }
  }

  private stopSeasonSyncPolling(): void {
    if (this.seasonSyncPollInterval) {
      clearInterval(this.seasonSyncPollInterval);
      this.seasonSyncPollInterval = null;
    }
  }
}
