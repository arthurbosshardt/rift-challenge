import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { RaceApiService } from '../../core/services/race-api.service';
import { RaceSummary } from '../../core/models/race.models';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { RaceCardComponent } from '../../shared/components/race-card/race-card.component';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-public-races-page',
  imports: [PageShellComponent, RaceCardComponent, LoaderComponent, TranslatePipe],
  templateUrl: './public-races-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './public-races-page.component.scss',
})
export class PublicRacesPageComponent implements OnInit {
  private readonly raceApi = inject(RaceApiService);
  private readonly i18n = inject(I18nService);

  protected readonly races = signal<RaceSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.raceApi.listPublicRaces().subscribe({
      next: (races) => {
        this.races.set(races);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.i18n.t('home.loadPublicError'));
        this.loading.set(false);
      },
    });
  }
}
