import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { RaceSummary } from '../../core/models/race.models';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { RaceCardComponent } from '../../shared/components/race-card/race-card.component';

@Component({
  selector: 'app-my-races-page',
  imports: [PageShellComponent, RaceCardComponent, RouterLink],
  templateUrl: './my-races-page.component.html',
  styleUrl: './my-races-page.component.scss',
})
export class MyRacesPageComponent implements OnInit {
  private readonly raceApi = inject(RaceApiService);
  protected readonly auth = inject(AuthService);

  protected readonly races = signal<RaceSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadRaces();
  }

  private async loadRaces(): Promise<void> {
    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      this.error.set('Session expirée. Reconnectez-vous.');
      this.loading.set(false);
      return;
    }

    this.raceApi.listMyRaces().subscribe({
      next: (races) => {
        this.races.set(races);
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.error.set('Session expirée. Reconnectez-vous.');
        } else {
          this.error.set('Impossible de charger vos races.');
        }
        this.loading.set(false);
      },
    });
  }
}
