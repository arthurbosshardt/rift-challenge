import { Component, inject, OnInit, signal } from '@angular/core';
import { RaceApiService } from '../../core/services/race-api.service';
import { RaceSummary } from '../../core/models/race.models';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { RaceCardComponent } from '../../shared/components/race-card/race-card.component';

@Component({
  selector: 'app-public-races-page',
  imports: [PageShellComponent, RaceCardComponent],
  templateUrl: './public-races-page.component.html',
  styleUrl: './public-races-page.component.scss',
})
export class PublicRacesPageComponent implements OnInit {
  private readonly raceApi = inject(RaceApiService);

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
        this.error.set('Impossible de charger les races publiques.');
        this.loading.set(false);
      },
    });
  }
}
