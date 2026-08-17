import { Component, computed, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { RaceSummary } from '../../core/models/race.models';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { RaceCardComponent } from '../../shared/components/race-card/race-card.component';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import {
  filterPublicRaces,
  hasActivePublicRaceFilters,
  PublicRaceTypeFilter,
} from '../../core/utils/filter-public-races';

@Component({
  selector: 'app-public-races-page',
  imports: [PageShellComponent, RaceCardComponent, LoaderComponent, TranslatePipe, RouterLink],
  templateUrl: './public-races-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './public-races-page.component.scss',
})
export class PublicRacesPageComponent implements OnInit {
  private readonly raceApi = inject(RaceApiService);
  protected readonly auth = inject(AuthService);
  protected readonly authModal = inject(AuthModalService);
  private readonly i18n = inject(I18nService);

  protected readonly allRaces = signal<RaceSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly refreshing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly raceNameQuery = signal('');
  protected readonly summonerQuery = signal('');
  protected readonly typeFilter = signal<PublicRaceTypeFilter>('ALL');
  protected readonly showLinkBanner = signal(false);

  protected readonly filteredRaces = computed(() =>
    filterPublicRaces(this.allRaces(), {
      raceName: this.raceNameQuery(),
      summoner: this.summonerQuery(),
      type: this.typeFilter(),
    }),
  );

  protected readonly hasActiveFilters = computed(() =>
    hasActivePublicRaceFilters({
      raceName: this.raceNameQuery(),
      summoner: this.summonerQuery(),
      type: this.typeFilter(),
    }),
  );

  ngOnInit(): void {
    void this.initPage();
  }

  protected onRaceNameInput(event: Event): void {
    this.raceNameQuery.set((event.target as HTMLInputElement).value);
  }

  protected onSummonerInput(event: Event): void {
    this.summonerQuery.set((event.target as HTMLInputElement).value);
  }

  protected setTypeFilter(filter: PublicRaceTypeFilter): void {
    this.typeFilter.set(filter);
  }

  protected clearSearch(): void {
    this.raceNameQuery.set('');
    this.summonerQuery.set('');
    this.typeFilter.set('ALL');
  }

  protected refreshRaces(): void {
    this.loadRaces(true);
  }

  private loadRaces(forceRefresh = false): void {
    if (this.refreshing() && !forceRefresh) {
      return;
    }

    const showFullScreenLoader = this.allRaces().length === 0;
    if (showFullScreenLoader) {
      this.loading.set(true);
    } else {
      this.refreshing.set(true);
    }
    this.error.set(null);

    this.raceApi.listPublicRaces().subscribe({
      next: (races) => {
        this.allRaces.set(races);
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
    this.loadRaces();
  }
}
