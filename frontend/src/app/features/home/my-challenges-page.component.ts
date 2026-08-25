import { Component, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { ActivityCacheService } from '../../core/services/activity-cache.service';
import { PublicChallengesCacheService } from '../../core/services/public-challenges-cache.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { ChallengeListResponse } from '../../core/models/challenge.models';
import { formatTimeSince } from '../../core/utils/relative-time';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';

@Component({
  selector: 'app-my-challenges-page',
  imports: [
    PageShellComponent,
    ChallengeCardComponent,
    ChallengeListSkeletonComponent,
    NavIconComponent,
    TranslatePipe,
  ],
  templateUrl: './my-challenges-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './my-challenges-page.component.scss',
})
export class MyChallengesPageComponent implements OnInit, OnDestroy {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly i18n = inject(I18nService);
  private readonly cache = inject(ActivityCacheService);
  private readonly publicCache = inject(PublicChallengesCacheService);
  protected readonly backend = inject(BackendStatusService);

  protected readonly challenges = this.cache.challenges;
  protected readonly challengesLoading = signal(this.cache.challengesLastLoadedAt() === null);
  protected readonly challengesRefreshing = signal(false);
  protected readonly challengesRefreshAvailable = this.publicCache.refreshAvailable;
  protected readonly challengesError = signal<string | null>(null);
  private challengesTickInterval: ReturnType<typeof setInterval> | null = null;
  protected readonly challengesNowMs = signal(Date.now());

  ngOnInit(): void {
    void this.loadPage();
  }

  ngOnDestroy(): void {
    this.stopChallengesCountdownTick();
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
      this.challengesError.set(this.i18n.t('home.sessionExpired'));
      this.challengesLoading.set(false);
      return;
    }

    const ownerKey = this.auth.userId();
    if (ownerKey) {
      this.cache.hydrateForOwner(ownerKey);
      this.challengesLoading.set(this.cache.challengesLastLoadedAt() === null);
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    if (!this.auth.linkedAccount()) {
      this.challengesLoading.set(false);
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
}
