import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { RaceApiService } from '../../core/services/race-api.service';
import { RaceType } from '../../core/models/race.models';
import { addDaysToIso, buildLocalStartAtIso } from '../../core/utils/race-date';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-create-race-page',
  imports: [FormsModule, RouterLink, PageShellComponent, TranslatePipe],
  templateUrl: './create-race-page.component.html',
  styleUrl: './create-race-page.component.scss',
})
export class CreateRacePageComponent {
  private readonly raceApi = inject(RaceApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected name = '';
  protected type: RaceType = 'SOLOQ';
  protected startDate = '';
  protected startHour = 12;
  protected endMode: 'duration' | 'date' = 'duration';
  protected durationDays = 7;
  protected endDate = '';
  protected endHour = 12;
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);
  protected readonly durationOptions = [1, 3, 7, 14, 21, 30];
  protected isPublic = false;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    this.error.set(null);

    const startAt = buildLocalStartAtIso(this.startDate, this.startHour);
    if (!startAt) {
      this.error.set(this.i18n.t('create.invalidDate'));
      return;
    }

    const endAt =
      this.endMode === 'duration'
        ? addDaysToIso(startAt, this.durationDays)
        : buildLocalStartAtIso(this.endDate, this.endHour);

    if (!endAt) {
      this.error.set(this.i18n.t('create.invalidEndDate'));
      return;
    }

    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      this.error.set(this.i18n.t('create.endBeforeStart'));
      return;
    }

    this.loading.set(true);

    this.raceApi
      .createRace({
        name: this.name.trim(),
        type: this.type,
        startAt,
        endAt,
        isPublic: this.isPublic,
      })
      .subscribe({
        next: async (race) => {
          await this.router.navigate(['/races', race.shareSlug]);
        },
        error: () => {
          this.error.set(this.i18n.t('create.error'));
          this.loading.set(false);
        },
      });
  }
}
