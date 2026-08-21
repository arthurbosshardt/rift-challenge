import { Component, computed, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';

import { AuthService } from '../../core/services/auth.service';

import { AuthModalService } from '../../core/services/auth-modal.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { PublicChallengesCacheService } from '../../core/services/public-challenges-cache.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { ChallengeListResponse, ChallengeRegion } from '../../core/models/challenge.models';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { formatTimeSince } from '../../core/utils/relative-time';

import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';

import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { ClampTooltipDirective } from '../../shared/directives/clamp-tooltip.directive';

import { TranslatePipe } from '../../core/i18n/t.pipe';

import { I18nService } from '../../core/i18n/i18n.service';

import {

  DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER,

  filterPublicChallenges,

  hasActivePublicChallengeFilters,

  PublicChallengeStatusFilter,

  PublicChallengeTypeFilter,

} from '../../core/utils/filter-public-challenges';

@Component({

  selector: 'app-public-challenges-page',

  imports: [PageShellComponent, ChallengeCardComponent, ChallengeListSkeletonComponent, TranslatePipe, NavIconComponent, ClampTooltipDirective],

  templateUrl: './public-challenges-page.component.html',

  changeDetection: ChangeDetectionStrategy.Eager,

  styleUrl: './public-challenges-page.component.scss',

})
export class PublicChallengesPageComponent implements OnInit, OnDestroy {

  private readonly challengeApi = inject(ChallengeApiService);

  protected readonly auth = inject(AuthService);

  protected readonly authModal = inject(AuthModalService);
  protected readonly settingsModal = inject(SettingsModalService);
  protected readonly backend = inject(BackendStatusService);
  private readonly cache = inject(PublicChallengesCacheService);

  private readonly i18n = inject(I18nService);



  protected readonly allChallenges = this.cache.challenges;

  protected readonly loading = signal(this.cache.lastLoadedAt() === null);

  protected readonly refreshing = signal(false);

  protected readonly refreshAvailable = this.cache.refreshAvailable;

  private tickInterval: ReturnType<typeof setInterval> | null = null;
  protected readonly nowMs = signal(Date.now());

  protected readonly error = signal<string | null>(null);

  protected readonly challengeNameQuery = signal('');

  protected readonly summonerQuery = signal('');

  protected readonly typeFilter = signal<PublicChallengeTypeFilter>('ALL');

  protected readonly statusFilter = signal<PublicChallengeStatusFilter>('ALL');

  protected readonly regionFilter = signal<ChallengeRegion>(DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER);

  protected readonly regionOptions: ChallengeRegion[] = ['EUW', 'EUNE', 'NA', 'KR'];

  protected readonly showLinkBanner = signal(false);



  protected readonly filteredChallenges = computed(() =>

    filterPublicChallenges(this.allChallenges(), {

      challengeName: this.challengeNameQuery(),

      summoner: this.summonerQuery(),

      type: this.typeFilter(),

      status: this.statusFilter(),

      region: this.regionFilter(),

    }),

  );



  protected readonly hasActiveFilters = computed(() =>

    hasActivePublicChallengeFilters({

      challengeName: this.challengeNameQuery(),

      summoner: this.summonerQuery(),

      type: this.typeFilter(),

      status: this.statusFilter(),

      region: this.regionFilter(),

    }),

  );



  ngOnInit(): void {

    void this.initPage();

  }

  ngOnDestroy(): void {
    this.stopCountdownTick();
  }



  protected onChallengeNameInput(event: Event): void {

    this.challengeNameQuery.set((event.target as HTMLInputElement).value);

  }



  protected onSummonerInput(event: Event): void {

    this.summonerQuery.set((event.target as HTMLInputElement).value);

  }



  protected setTypeFilter(filter: PublicChallengeTypeFilter): void {

    this.typeFilter.set(filter);

  }

  protected setStatusFilter(filter: PublicChallengeStatusFilter): void {
    this.statusFilter.set(filter);
  }

  protected onRegionFilterChange(event: Event): void {
    this.regionFilter.set((event.target as HTMLSelectElement).value as ChallengeRegion);
  }

  protected regionLabel(region: ChallengeRegion): string {
    switch (region) {
      case 'EUNE':
        return this.i18n.t('challenge.regionEune');
      case 'NA':
        return this.i18n.t('challenge.regionNa');
      case 'KR':
        return this.i18n.t('challenge.regionKr');
      default:
        return this.i18n.t('challenge.regionEuw');
    }
  }



  protected clearSearch(): void {

    this.challengeNameQuery.set('');

    this.summonerQuery.set('');

    this.typeFilter.set('ALL');

    this.statusFilter.set('ALL');

    this.regionFilter.set(DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER);

  }



  protected refreshChallenges(): void {
    if (!this.refreshAvailable() || this.refreshing()) {
      return;
    }

    this.refreshing.set(true);
    this.error.set(null);

    this.challengeApi.refreshPublicChallenges().subscribe({
      next: (response) => this.applyResponse(response),
      error: (err: { status?: number }) => {
        this.error.set(
          err.status === 429 ? this.i18n.t('home.refreshOnCooldown') : this.i18n.t('home.loadPublicError'),
        );
        this.refreshing.set(false);
      },
    });
  }

  protected refreshButtonLabel(): string {
    const nextAvailable = this.cache.nextRefreshAvailableAt();
    if (!this.refreshAvailable() && nextAvailable) {
      const countdown = formatRefreshCountdown(nextAvailable, this.nowMs());
      if (countdown) {
        return countdown;
      }
    }
    return this.i18n.t('home.searchRefreshLabel');
  }

  protected lastUpdatedLabel(): string | null {
    const generatedAt = this.cache.generatedAt();
    if (!generatedAt) {
      return null;
    }
    const time = formatTimeSince(generatedAt, this.nowMs(), this.i18n.locale());
    if (!time) {
      return null;
    }
    return this.i18n.t('activity.lastRefreshed', { time });
  }



  private loadChallenges(): void {

    const showFullScreenLoader = this.allChallenges().length === 0;

    if (showFullScreenLoader) {

      this.loading.set(true);

    } else {

      this.refreshing.set(true);

    }

    this.error.set(null);



    this.challengeApi.listPublicChallenges().subscribe({

      next: (response) => this.applyResponse(response),

      error: () => {

        this.loading.set(false);

        this.refreshing.set(false);

        if (this.backend.ready()) {
          this.error.set(this.i18n.t('home.loadPublicError'));
        } else {
          this.error.set(this.i18n.t('backend.wakingUpMessage'));
          this.backend.onReady(() => this.loadChallenges());
        }

      },

    });

  }

  private applyResponse(response: ChallengeListResponse): void {
    this.allChallenges.set(response.challenges);
    this.cache.lastLoadedAt.set(Date.now());
    this.cache.generatedAt.set(response.generatedAt);
    this.cache.refreshAvailable.set(response.refreshAvailable);
    this.cache.nextRefreshAvailableAt.set(response.nextRefreshAvailableAt);
    this.loading.set(false);
    this.refreshing.set(false);

    if (response.refreshAvailable) {
      this.stopCountdownTick();
    } else {
      this.startCountdownTick();
    }
  }

  private startCountdownTick(): void {
    this.stopCountdownTick();
    this.tickInterval = setInterval(() => {
      this.nowMs.set(Date.now());
      const nextAvailable = this.cache.nextRefreshAvailableAt();
      if (this.cache.refreshAvailable() || !nextAvailable || !formatRefreshCountdown(nextAvailable, this.nowMs())) {
        this.cache.refreshAvailable.set(true);
        this.stopCountdownTick();
      }
    }, 1000);
  }

  private stopCountdownTick(): void {
    if (this.tickInterval) {
      clearInterval(this.tickInterval);
      this.tickInterval = null;
    }
  }



  private async initPage(): Promise<void> {

    await this.auth.waitUntilReady();

    if (this.auth.isAuthenticated() && !this.auth.linkedAccount()) {

      await this.auth.refreshProfile();

    }

    this.showLinkBanner.set(

      !this.auth.isProfileLoading() && (!this.auth.isAuthenticated() || !this.auth.linkedAccount()),

    );

    if (this.cache.lastLoadedAt() === null) {
      this.loadChallenges();
    } else {
      this.loading.set(false);
      if (!this.cache.refreshAvailable()) {
        this.startCountdownTick();
      }
    }

  }

}


