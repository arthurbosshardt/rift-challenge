import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { RaceApiService } from '../../core/services/race-api.service';
import { RaceType } from '../../core/models/race.models';
import { buildLocalStartAtIso } from '../../core/utils/race-date';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

@Component({
  selector: 'app-create-race-page',
  imports: [FormsModule, RouterLink, PageShellComponent],
  templateUrl: './create-race-page.component.html',
  styleUrl: './create-race-page.component.scss',
})
export class CreateRacePageComponent {
  private readonly raceApi = inject(RaceApiService);
  private readonly router = inject(Router);

  protected name = '';
  protected type: RaceType = 'SOLOQ';
  protected startDate = '';
  protected startHour = 12;
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);
  protected isPublic = false;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    this.error.set(null);

    const startAt = buildLocalStartAtIso(this.startDate, this.startHour);
    if (!startAt) {
      this.error.set('La date de début est invalide.');
      return;
    }

    this.loading.set(true);

    this.raceApi
      .createRace({
        name: this.name.trim(),
        type: this.type,
        startAt,
        isPublic: this.isPublic,
      })
      .subscribe({
        next: async (race) => {
          await this.router.navigate(['/races', race.shareSlug]);
        },
        error: () => {
          this.error.set('Impossible de créer la race.');
          this.loading.set(false);
        },
      });
  }
}
