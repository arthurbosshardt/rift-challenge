import {
  Component,
  computed,
  inject,
  OnInit,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChallengeApiService } from '../../core/services/challenge-api.service';

import { AuthService } from '../../core/services/auth.service';

import { AuthModalService } from '../../core/services/auth-modal.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { PublicChallengesCacheService } from '../../core/services/public-challenges-cache.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { ChallengeListResponse, ChallengeRegion } from '../../core/models/challenge.models';
import { regionLabel as sharedRegionLabel } from '../../core/utils/region-display';

import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';

import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { ClampTooltipDirective } from '../../shared/directives/clamp-tooltip.directive';

import { TranslatePipe } from '../../core/i18n/t.pipe';

import { I18nService } from '../../core/i18n/i18n.service';

import {
  DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER,
  DEFAULT_PUBLIC_CHALLENGE_STATUS_FILTER,
  filterPublicChallenges,
  hasActivePublicChallengeFilters,
  PublicChallengeStatusFilter,
  PublicChallengeTypeFilter,
} from '../../core/utils/filter-public-challenges';

@Component({
  selector: 'app-public-challenges-page',

  imports: [
    RouterLink,
    PageShellComponent,
    ChallengeCardComponent,
    ChallengeListSkeletonComponent,
    TranslatePipe,
    NavIconComponent,
    ClampTooltipDirective,
  ],

  templateUrl: './public-challenges-page.component.html',

  changeDetection: ChangeDetectionStrategy.OnPush,

  styleUrl: './public-challenges-page.component.scss',
})
export class PublicChallengesPageComponent implements OnInit {
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

  protected readonly error = signal<string | null>(null);

  protected readonly challengeNameQuery = signal('');

  protected readonly summonerQuery = signal('');

  protected readonly typeFilter = signal<PublicChallengeTypeFilter>('ALL');

  protected readonly statusFilter = signal<PublicChallengeStatusFilter>(
    DEFAULT_PUBLIC_CHALLENGE_STATUS_FILTER,
  );

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
    return sharedRegionLabel(region, this.i18n);
  }

  protected clearSearch(): void {
    this.challengeNameQuery.set('');

    this.summonerQuery.set('');

    this.typeFilter.set('ALL');

    this.statusFilter.set(DEFAULT_PUBLIC_CHALLENGE_STATUS_FILTER);

    this.regionFilter.set(DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER);
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
    this.loading.set(false);
    this.refreshing.set(false);
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
    }
  }
}
