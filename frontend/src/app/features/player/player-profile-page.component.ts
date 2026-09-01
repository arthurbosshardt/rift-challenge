import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { PlayerApiService } from '../../core/services/player-api.service';
import { SummonerSuggestion } from '../../core/services/summoner-search.service';
import {
  ActivityAccount,
  applySyncBaseline,
  normalizeActivityAccount,
} from '../../core/services/activity-cache.service';
import { AccountRecentGames } from '../../core/models/challenge.models';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { SeoService } from '../../core/seo/seo.service';
import { RefreshCooldown } from '../../core/utils/refresh-cooldown';
import { formatTimeSince } from '../../core/utils/relative-time';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ActivityAccountCardComponent } from '../../shared/components/activity-account-card/activity-account-card.component';
import { MatchHistorySkeletonComponent } from '../../shared/components/match-history-skeleton/match-history-skeleton.component';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { GameDetailModalService } from '../../shared/services/game-detail-modal.service';
import { copyTextToClipboard } from '../../core/utils/clipboard';

@Component({
  selector: 'app-player-profile-page',
  imports: [
    PageShellComponent,
    ActivityAccountCardComponent,
    MatchHistorySkeletonComponent,
    PlayerAvatarComponent,
    SkeletonComponent,
    TranslatePipe,
  ],
  templateUrl: './player-profile-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './player-profile-page.component.scss',
})
export class PlayerProfilePageComponent implements OnInit, OnDestroy {
  /** A never-synced account's first resolve/activity request can transiently fail while its
   *  riot_account row is being created — retry silently instead of flashing an error. */
  private static readonly MAX_LOAD_RETRIES = 5;
  private static readonly LOAD_RETRY_DELAY_MS = 2_500;

  private readonly route = inject(ActivatedRoute);
  private readonly playerApi = inject(PlayerApiService);
  private readonly gameDetailModal = inject(GameDetailModalService);
  private readonly i18n = inject(I18nService);
  private readonly seo = inject(SeoService);
  protected readonly backend = inject(BackendStatusService);

  protected readonly player = signal<SummonerSuggestion | null>(null);
  protected readonly notFound = signal(false);

  protected readonly activityAccount = signal<ActivityAccount | null>(null);
  protected readonly activityLoading = signal(true);
  protected readonly activityError = signal<string | null>(null);
  protected readonly lastRefreshedAt = signal<string | null>(null);
  private readonly refreshCooldown = new RefreshCooldown();
  protected readonly refreshCooldownSeconds = this.refreshCooldown.seconds;

  protected readonly riotIdCopied = signal(false);

  private riotId = '';
  private seasonSyncPollInterval: ReturnType<typeof setInterval> | null = null;
  private paramSubscription: Subscription | null = null;
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;
  private activityRetryTimer: ReturnType<typeof setTimeout> | null = null;
  private activityRetryCount = 0;
  private resolveRetryTimer: ReturnType<typeof setTimeout> | null = null;
  private resolveRetryCount = 0;

  ngOnInit(): void {
    this.paramSubscription = this.route.paramMap.subscribe((params) => {
      this.loadPlayer(params.get('riotId') ?? '');
    });
  }

  ngOnDestroy(): void {
    this.paramSubscription?.unsubscribe();
    this.refreshCooldown.clear();
    this.stopSeasonSyncPolling();
    this.clearActivityRetry();
    this.clearResolveRetry();
    if (this.copyResetTimer) {
      clearTimeout(this.copyResetTimer);
    }
  }

  private loadPlayer(riotId: string): void {
    this.stopSeasonSyncPolling();
    this.clearActivityRetry();
    this.clearResolveRetry();
    this.riotId = riotId;
    this.player.set(null);
    this.notFound.set(false);
    this.activityAccount.set(null);
    this.activityError.set(null);
    this.lastRefreshedAt.set(null);

    if (!riotId) {
      this.notFound.set(true);
      this.activityLoading.set(false);
      return;
    }

    this.resolvePlayer(riotId);
    this.loadActivity(false, true);
  }

  private resolvePlayer(riotId: string): void {
    this.playerApi.resolve(riotId).subscribe({
      next: (player) => {
        if (riotId !== this.riotId) {
          return;
        }
        this.resolveRetryCount = 0;
        this.player.set(player);
        this.applySeo(player);
      },
      error: (err: HttpErrorResponse) => {
        if (riotId !== this.riotId) {
          return;
        }
        if (err.status !== 404 && this.resolveRetryCount < PlayerProfilePageComponent.MAX_LOAD_RETRIES) {
          this.resolveRetryCount++;
          this.resolveRetryTimer = setTimeout(() => {
            if (riotId === this.riotId) {
              this.resolvePlayer(riotId);
            }
          }, PlayerProfilePageComponent.LOAD_RETRY_DELAY_MS);
          return;
        }
        this.notFound.set(true);
        this.activityLoading.set(false);
        this.applyNotFoundSeo();
      },
    });
  }

  protected titleFor(key: 'player.statsTitle'): string {
    const name = this.player()?.gameName ?? this.riotId;
    return this.i18n.t(key, { name });
  }

  protected displayName(): string {
    return this.player()?.gameName ?? this.riotId;
  }

  protected profileIconId(): number | null {
    return this.player()?.profileIconId ?? null;
  }

  protected tier(): string | null {
    return this.activityAccount()?.tier ?? null;
  }

  protected riotIdLabel(): string | null {
    return this.player()?.riotId ?? null;
  }

  protected copyRiotIdAria(): string {
    return this.i18n.t('player.copyRiotIdAria', { riotId: this.riotIdLabel() ?? this.riotId });
  }

  protected copyRiotId(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const riotId = this.riotIdLabel();
    if (!riotId) {
      return;
    }
    void this.performCopyRiotId(riotId);
  }

  private async performCopyRiotId(riotId: string): Promise<void> {
    if (!(await copyTextToClipboard(riotId))) {
      return;
    }
    this.riotIdCopied.set(true);
    if (this.copyResetTimer) {
      clearTimeout(this.copyResetTimer);
    }
    this.copyResetTimer = setTimeout(() => this.riotIdCopied.set(false), 1500);
  }

  protected openGameDetail(matchId: string): void {
    const puuid = this.player()?.puuid;
    if (!puuid) {
      return;
    }
    this.gameDetailModal.open(matchId, { type: 'leaderboard', puuid });
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
    const seconds = this.refreshCooldownSeconds();
    if (seconds > 0) {
      return this.i18n.locale() === 'en' ? `${seconds} seconds` : `${seconds} secondes`;
    }
    return this.i18n.t('activity.refreshLabel');
  }

  protected refreshDisabled(): boolean {
    return (
      this.activityLoading() ||
      this.refreshCooldown.active ||
      !this.backend.ready() ||
      this.notFound() ||
      this.hasIncompleteSeasonSync()
    );
  }

  private hasIncompleteSeasonSync(): boolean {
    return this.activityAccount()?.seasonSyncInProgress ?? false;
  }

  protected refreshActivity(): void {
    if (this.refreshDisabled()) {
      return;
    }
    this.activityError.set(null);
    this.activityLoading.set(true);
    const riotId = this.riotId;
    this.playerApi.refreshActivity(riotId).subscribe({
      next: (activity) => this.handleActivitySuccess(riotId, activity),
      error: (err: HttpErrorResponse) => this.handleActivityError(riotId, err, false),
    });
    this.refreshCooldown.start();
  }

  private loadActivity(silent = false, allowRetry = false): void {
    if (!silent) {
      this.activityLoading.set(true);
    }
    const riotId = this.riotId;
    this.playerApi.getActivity(riotId).subscribe({
      next: (activity) => this.handleActivitySuccess(riotId, activity),
      error: (err: HttpErrorResponse) => this.handleActivityError(riotId, err, allowRetry),
    });
  }

  private handleActivitySuccess(riotId: string, activity: AccountRecentGames): void {
    if (riotId !== this.riotId) {
      return;
    }
    this.activityRetryCount = 0;
    const normalized = normalizeActivityAccount(activity);
    const previous = this.activityAccount();
    if (!previous || this.hasNewActivityData(previous, normalized)) {
      this.lastRefreshedAt.set(new Date().toISOString());
    }
    this.activityAccount.set(applySyncBaseline(normalized, false, previous ?? undefined));
    this.activityError.set(null);
    this.activityLoading.set(false);
    this.updateSeasonSyncPolling();
  }

  private handleActivityError(riotId: string, err: HttpErrorResponse, allowRetry: boolean): void {
    if (riotId !== this.riotId) {
      return;
    }
    if (err.status === 404) {
      this.activityLoading.set(false);
      return;
    }
    if (allowRetry && this.activityRetryCount < PlayerProfilePageComponent.MAX_LOAD_RETRIES) {
      this.activityRetryCount++;
      this.activityRetryTimer = setTimeout(() => {
        if (riotId === this.riotId) {
          this.loadActivity(true, true);
        }
      }, PlayerProfilePageComponent.LOAD_RETRY_DELAY_MS);
      return;
    }
    this.activityLoading.set(false);
    this.activityError.set(
      err.status === 429 ? this.i18n.t('home.refreshOnCooldown') : this.i18n.t('activity.loadError'),
    );
  }

  private hasNewActivityData(previous: ActivityAccount, next: ActivityAccount): boolean {
    return (
      next.syncedGames !== previous.syncedGames ||
      next.wins !== previous.wins ||
      next.losses !== previous.losses ||
      next.games[0]?.id !== previous.games[0]?.id
    );
  }

  private clearActivityRetry(): void {
    if (this.activityRetryTimer) {
      clearTimeout(this.activityRetryTimer);
      this.activityRetryTimer = null;
    }
    this.activityRetryCount = 0;
  }

  private clearResolveRetry(): void {
    if (this.resolveRetryTimer) {
      clearTimeout(this.resolveRetryTimer);
      this.resolveRetryTimer = null;
    }
    this.resolveRetryCount = 0;
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

  private applySeo(player: SummonerSuggestion): void {
    this.seo.apply({
      title: `${this.i18n.t('seo.player.title', { name: player.gameName })} | Rift Challenge`,
      description: this.i18n.t('seo.player.description', { name: player.gameName }),
      path: this.canonicalPlayerPath(),
    });
  }

  private applyNotFoundSeo(): void {
    this.seo.apply({
      title: `${this.i18n.t('seo.player.notFound.title')} | Rift Challenge`,
      description: this.i18n.t('seo.player.notFound.description'),
      path: this.canonicalPlayerPath(),
      noindex: true,
    });
  }

  /**
   * Riot ID lookups are case-insensitive, so `/players/Foo#EUW` and `/players/foo#euw` are the
   * same profile. Lowercasing here (display elsewhere still uses the properly-cased riotId from
   * the resolved player) avoids splitting indexing signal across both casings.
   */
  private canonicalPlayerPath(): string {
    return `/players/${encodeURIComponent(this.riotId.toLowerCase())}`;
  }
}
