import {
  Component,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RaceApiService } from '../../core/services/race-api.service';
import { CreateRaceModalService } from '../../core/services/create-race-modal.service';
import { RaceType } from '../../core/models/race.models';
import { addDaysToIso, buildLocalStartAtIso } from '../../core/utils/race-date';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

type EndMode = 'duration' | 'date';

type InvalidField = 'name' | 'startDate' | 'endDate';

type DurationOption = {
  days: number;
  labelKey:
    | 'create.duration1Week'
    | 'create.duration2Weeks'
    | 'create.duration1Month'
    | 'create.duration3Months';
};

@Component({
  selector: 'app-create-race-modal',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './create-race-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './create-race-modal.component.scss',
})
export class CreateRaceModalComponent {
  protected readonly createRaceModal = inject(CreateRaceModalService);
  private readonly raceApi = inject(RaceApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected name = '';
  protected type: RaceType = 'SOLOQ';
  protected startDate = '';
  protected startHour = 12;
  protected endMode: EndMode = 'duration';
  protected durationDays = 30;
  protected endDate = '';
  protected endHour = 12;
  protected readonly durationOptions: DurationOption[] = [
    { days: 7, labelKey: 'create.duration1Week' },
    { days: 14, labelKey: 'create.duration2Weeks' },
    { days: 30, labelKey: 'create.duration1Month' },
    { days: 90, labelKey: 'create.duration3Months' },
  ];
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);
  protected isPublic = false;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly invalidFields = signal<ReadonlySet<InvalidField>>(new Set());
  protected readonly fieldErrors = signal<Partial<Record<InvalidField, string>>>({});

  constructor() {
    effect(() => {
      if (this.createRaceModal.isOpen()) {
        this.resetForm();
        document.body.style.overflow = 'hidden';
        return;
      }

      document.body.style.overflow = '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.createRaceModal.isOpen() && !this.loading()) {
      this.close();
    }
  }

  protected close(): void {
    this.createRaceModal.close();
  }

  protected isInvalid(field: InvalidField): boolean {
    return this.invalidFields().has(field);
  }

  protected fieldError(field: InvalidField): string | null {
    return this.fieldErrors()[field] ?? null;
  }

  protected clearInvalid(field: InvalidField): void {
    if (!this.invalidFields().has(field)) {
      return;
    }

    const nextFields = new Set(this.invalidFields());
    nextFields.delete(field);

    const nextErrors = { ...this.fieldErrors() };
    delete nextErrors[field];

    this.invalidFields.set(nextFields);
    this.fieldErrors.set(nextErrors);

    if (nextFields.size === 0) {
      this.error.set(null);
    }
  }

  protected submit(): void {
    this.clearValidation();

    const invalidFields = new Set<InvalidField>();
    const fieldErrors: Partial<Record<InvalidField, string>> = {};
    const requiredMessage = this.i18n.t('create.fieldRequired');

    if (!this.name.trim()) {
      invalidFields.add('name');
      fieldErrors.name = requiredMessage;
    }

    if (!this.startDate.trim()) {
      invalidFields.add('startDate');
      fieldErrors.startDate = requiredMessage;
    }

    if (this.endMode === 'date' && !this.endDate.trim()) {
      invalidFields.add('endDate');
      fieldErrors.endDate = requiredMessage;
    }

    if (invalidFields.size > 0) {
      this.invalidFields.set(invalidFields);
      this.fieldErrors.set(fieldErrors);
      this.error.set(this.i18n.t('create.formIncomplete'));
      return;
    }

    const startAt = buildLocalStartAtIso(this.startDate, this.startHour);
    if (!startAt) {
      this.invalidFields.set(new Set(['startDate']));
      this.fieldErrors.set({ startDate: this.i18n.t('create.invalidStartDate') });
      this.error.set(this.i18n.t('create.invalidStartDate'));
      return;
    }

    const endAt =
      this.endMode === 'duration'
        ? addDaysToIso(startAt, this.durationDays)
        : buildLocalStartAtIso(this.endDate, this.endHour);
    if (!endAt) {
      this.invalidFields.set(new Set(['endDate']));
      this.fieldErrors.set({ endDate: this.i18n.t('create.invalidEndDate') });
      this.error.set(this.i18n.t('create.invalidEndDate'));
      return;
    }

    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      if (this.endMode === 'date') {
        this.invalidFields.set(new Set(['endDate']));
        this.fieldErrors.set({ endDate: this.i18n.t('create.endBeforeStart') });
      }
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
          this.createRaceModal.close();
          await this.router.navigate(['/races', race.shareSlug]);
        },
        error: () => {
          this.error.set(this.i18n.t('create.error'));
          this.loading.set(false);
        },
      });
  }

  private clearValidation(): void {
    this.invalidFields.set(new Set());
    this.fieldErrors.set({});
    this.error.set(null);
  }

  private resetForm(): void {
    this.name = '';
    this.type = 'SOLOQ';
    this.startDate = '';
    this.startHour = 12;
    this.endMode = 'duration';
    this.durationDays = 30;
    this.endDate = '';
    this.endHour = 12;
    this.isPublic = false;
    this.loading.set(false);
    this.clearValidation();
  }
}
