import { Component, computed, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';

import { AuthService } from '../../core/services/auth.service';

import { AuthModalService } from '../../core/services/auth-modal.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';

import { ChallengeSummary } from '../../core/models/challenge.models';

import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';

import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { ClampTooltipDirective } from '../../shared/directives/clamp-tooltip.directive';

import { TranslatePipe } from '../../core/i18n/t.pipe';

import { I18nService } from '../../core/i18n/i18n.service';

import {

  filterPublicChallenges,

  hasActivePublicChallengeFilters,

  PublicChallengeTypeFilter,

} from '../../core/utils/filter-public-challenges';



@Component({

  selector: 'app-public-challenges-page',

  imports: [PageShellComponent, ChallengeCardComponent, ChallengeListSkeletonComponent, TranslatePipe, NavIconComponent, ClampTooltipDirective],

  templateUrl: './public-challenges-page.component.html',

  changeDetection: ChangeDetectionStrategy.Eager,

  styleUrl: './public-challenges-page.component.scss',

})

export class PublicChallengesPageComponent implements OnInit {

  private readonly challengeApi = inject(ChallengeApiService);

  protected readonly auth = inject(AuthService);

  protected readonly authModal = inject(AuthModalService);
  protected readonly settingsModal = inject(SettingsModalService);

  private readonly i18n = inject(I18nService);



  protected readonly allChallenges = signal<ChallengeSummary[]>([]);

  protected readonly loading = signal(true);

  protected readonly refreshing = signal(false);

  protected readonly error = signal<string | null>(null);

  protected readonly challengeNameQuery = signal('');

  protected readonly summonerQuery = signal('');

  protected readonly typeFilter = signal<PublicChallengeTypeFilter>('ALL');

  protected readonly showLinkBanner = signal(false);



  protected readonly filteredChallenges = computed(() =>

    filterPublicChallenges(this.allChallenges(), {

      challengeName: this.challengeNameQuery(),

      summoner: this.summonerQuery(),

      type: this.typeFilter(),

    }),

  );



  protected readonly hasActiveFilters = computed(() =>

    hasActivePublicChallengeFilters({

      challengeName: this.challengeNameQuery(),

      summoner: this.summonerQuery(),

      type: this.typeFilter(),

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



  protected clearSearch(): void {

    this.challengeNameQuery.set('');

    this.summonerQuery.set('');

    this.typeFilter.set('ALL');

  }



  protected refreshChallenges(): void {

    this.loadChallenges(true);

  }



  private loadChallenges(forceRefresh = false): void {

    if (this.refreshing() && !forceRefresh) {

      return;

    }



    const showFullScreenLoader = this.allChallenges().length === 0;

    if (showFullScreenLoader) {

      this.loading.set(true);

    } else {

      this.refreshing.set(true);

    }

    this.error.set(null);



    this.challengeApi.listPublicChallenges(forceRefresh).subscribe({

      next: (challenges) => {

        this.allChallenges.set(challenges);

        this.loading.set(false);

        this.refreshing.set(false);

      },

      error: () => {

        this.error.set(this.i18n.t('home.loadPublicError'));

        this.loading.set(false);

        this.refreshing.set(false);

      },

    });

  }



  private async initPage(): Promise<void> {

    await this.auth.waitUntilReady();

    if (this.auth.isAuthenticated() && !this.auth.linkedAccount()) {

      await this.auth.refreshProfile();

    }

    this.showLinkBanner.set(

      !this.auth.isProfileLoading() && (!this.auth.isAuthenticated() || !this.auth.linkedAccount()),

    );

    this.loadChallenges();

  }

}


