import {
  Component,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RaceApiService } from '../../core/services/race-api.service';
import { EditRaceModalService } from '../../core/services/edit-race-modal.service';
import { DuoProgress, ParticipantProgress, RaceDetail } from '../../core/models/race.models';
import { buildLocalStartAtIso, splitLocalDateHour } from '../../core/utils/race-date';
import { normalizeRaceDetail } from '../../core/utils/race-detail';
import { mapParticipantError } from '../../core/utils/race-participant-errors';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { buildRiotId, normalizeGameName, normalizeTagLine } from '../../core/utils/riot-id';

type ScheduleInvalidField = 'startDate' | 'endDate';
type NameInvalidField = 'name';

type ParticipantInvalidField =
  | 'gameName'
  | 'tagLine'
  | 'duoPlayer1GameName'
  | 'duoPlayer1TagLine'
  | 'duoPlayer2GameName'
  | 'duoPlayer2TagLine';

@Component({
  selector: 'app-edit-race-modal',
  imports: [FormsModule, TranslatePipe, PlayerIdentityComponent],
  templateUrl: './edit-race-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './edit-race-modal.component.scss',
})
export class EditRaceModalComponent {
  protected readonly editRaceModal = inject(EditRaceModalService);
  private readonly raceApi = inject(RaceApiService);
  private readonly i18n = inject(I18nService);

  protected startDateInput = '';
  protected startHourInput = 12;
  protected endDateInput = '';
  protected endHourInput = 12;
  protected nameInput = '';
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);

  protected gameNameInput = '';
  protected tagLineInput = '';
  protected duoPlayer1GameName = '';
  protected duoPlayer1TagLine = '';
  protected duoPlayer2GameName = '';
  protected duoPlayer2TagLine = '';
  protected isPublicInput = false;

  protected readonly savingSchedule = signal(false);
  protected readonly savingName = signal(false);
  protected readonly nameFormError = signal<string | null>(null);
  protected readonly nameInvalidFields = signal<ReadonlySet<NameInvalidField>>(new Set());
  protected readonly nameFieldErrors = signal<Partial<Record<NameInvalidField, string>>>({});
  protected readonly nameSuccess = signal(false);
  protected readonly scheduleFormError = signal<string | null>(null);
  protected readonly scheduleInvalidFields = signal<ReadonlySet<ScheduleInvalidField>>(new Set());
  protected readonly scheduleFieldErrors = signal<Partial<Record<ScheduleInvalidField, string>>>({});
  protected readonly scheduleSuccess = signal(false);
  protected readonly savingVisibility = signal(false);
  protected readonly visibilityFormError = signal<string | null>(null);
  protected readonly visibilitySuccess = signal(false);
  protected readonly addingParticipant = signal(false);
  protected readonly participantFormError = signal<string | null>(null);
  protected readonly participantInvalidFields = signal<ReadonlySet<ParticipantInvalidField>>(new Set());
  protected readonly participantFieldErrors = signal<Partial<Record<ParticipantInvalidField, string>>>({});
  protected readonly removingParticipantId = signal<string | null>(null);
  protected readonly removingDuoId = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.editRaceModal.isOpen()) {
        const race = this.editRaceModal.race();
        if (race) {
          this.syncScheduleInputs(race);
          this.nameInput = race.name;
          this.isPublicInput = race.isPublic;
          this.resetParticipantInputs();
          this.clearScheduleValidation();
          this.clearNameValidation();
          this.clearParticipantValidation();
          this.visibilityFormError.set(null);
          this.visibilitySuccess.set(false);
          this.nameSuccess.set(false);
          this.scheduleSuccess.set(false);
        }
        document.body.style.overflow = 'hidden';
        return;
      }

      document.body.style.overflow = '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.editRaceModal.isOpen() && !this.savingSchedule() && !this.savingName() && !this.addingParticipant() && !this.savingVisibility()) {
      this.close();
    }
  }

  protected close(): void {
    this.editRaceModal.close();
  }

  protected typeLabel(type: RaceDetail['type']): string {
    return type === 'SOLOQ' ? this.i18n.t('race.typeSoloq') : this.i18n.t('race.typeDuoq');
  }

  protected entryCount(race: RaceDetail): number {
    return race.type === 'DUOQ' ? race.duos.length : race.participants.length;
  }

  protected entryLimit(race: RaceDetail): number {
    return race.type === 'DUOQ' ? 8 : 16;
  }

  protected participantListColumns(race: RaceDetail): 1 | 2 | 3 {
    const count = this.entryCount(race);
    if (race.type === 'DUOQ') {
      if (count >= 7) {
        return 3;
      }
      if (count >= 5) {
        return 2;
      }
      return 1;
    }
    if (count >= 11) {
      return 3;
    }
    if (count >= 7) {
      return 2;
    }
    return 1;
  }

  protected participantListClass(race: RaceDetail): string {
    const columns = this.participantListColumns(race);
    return columns === 1
      ? 'edit-race-modal__list'
      : `edit-race-modal__list edit-race-modal__list--cols-${columns}`;
  }

  protected duoLabel(duo: DuoProgress): string {
    return `${duo.player1.riotId} / ${duo.player2.riotId}`;
  }

  protected isScheduleInvalid(field: ScheduleInvalidField): boolean {
    return this.scheduleInvalidFields().has(field);
  }

  protected scheduleFieldError(field: ScheduleInvalidField): string | null {
    return this.scheduleFieldErrors()[field] ?? null;
  }

  protected clearScheduleInvalid(field: ScheduleInvalidField): void {
    if (!this.scheduleInvalidFields().has(field)) {
      return;
    }

    const nextFields = new Set(this.scheduleInvalidFields());
    nextFields.delete(field);

    const nextErrors = { ...this.scheduleFieldErrors() };
    delete nextErrors[field];

    this.scheduleInvalidFields.set(nextFields);
    this.scheduleFieldErrors.set(nextErrors);

    if (nextFields.size === 0) {
      this.scheduleFormError.set(null);
    }
  }

  protected isNameInvalid(field: NameInvalidField): boolean {
    return this.nameInvalidFields().has(field);
  }

  protected nameFieldError(field: NameInvalidField): string | null {
    return this.nameFieldErrors()[field] ?? null;
  }

  protected clearNameInvalid(field: NameInvalidField): void {
    if (!this.nameInvalidFields().has(field)) {
      return;
    }

    const nextFields = new Set(this.nameInvalidFields());
    nextFields.delete(field);

    const nextErrors = { ...this.nameFieldErrors() };
    delete nextErrors[field];

    this.nameInvalidFields.set(nextFields);
    this.nameFieldErrors.set(nextErrors);

    if (nextFields.size === 0) {
      this.nameFormError.set(null);
    }
  }

  protected saveName(): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || this.savingName()) {
      return;
    }

    this.clearNameValidation();

    const trimmed = this.nameInput.trim();
    if (!trimmed) {
      this.nameInvalidFields.set(new Set(['name']));
      this.nameFieldErrors.set({ name: this.i18n.t('create.fieldRequired') });
      this.nameFormError.set(this.i18n.t('create.formIncomplete'));
      return;
    }

    if (trimmed === race.name) {
      return;
    }

    this.savingName.set(true);
    this.nameFormError.set(null);
    this.nameSuccess.set(false);

    this.raceApi.updateRaceName(race.id, { name: trimmed }).subscribe({
      next: (updated) => {
        const normalized = normalizeRaceDetail(updated);
        this.editRaceModal.race.set(normalized);
        this.nameInput = normalized.name;
        this.savingName.set(false);
        this.nameSuccess.set(true);
        this.editRaceModal.notifyUpdated();
        window.setTimeout(() => this.nameSuccess.set(false), 2500);
      },
      error: () => {
        this.nameFormError.set(this.i18n.t('race.nameUpdateError'));
        this.savingName.set(false);
      },
    });
  }

  protected isParticipantInvalid(field: ParticipantInvalidField): boolean {
    return this.participantInvalidFields().has(field);
  }

  protected participantFieldError(field: ParticipantInvalidField): string | null {
    return this.participantFieldErrors()[field] ?? null;
  }

  protected clearParticipantInvalid(field: ParticipantInvalidField): void {
    if (!this.participantInvalidFields().has(field)) {
      return;
    }

    const nextFields = new Set(this.participantInvalidFields());
    nextFields.delete(field);

    const nextErrors = { ...this.participantFieldErrors() };
    delete nextErrors[field];

    this.participantInvalidFields.set(nextFields);
    this.participantFieldErrors.set(nextErrors);

    if (nextFields.size === 0) {
      this.participantFormError.set(null);
    }
  }

  protected toggleVisibility(): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || this.savingVisibility() || this.savingSchedule() || this.savingName() || this.addingParticipant()) {
      return;
    }

    const nextIsPublic = !this.isPublicInput;
    if (nextIsPublic === race.isPublic) {
      return;
    }

    this.isPublicInput = nextIsPublic;
    this.visibilityFormError.set(null);
    this.visibilitySuccess.set(false);
    this.savingVisibility.set(true);

    this.raceApi.updateRaceVisibility(race.id, { isPublic: nextIsPublic }).subscribe({
      next: (updated) => {
        const normalized = normalizeRaceDetail(updated);
        this.editRaceModal.race.set(normalized);
        this.isPublicInput = normalized.isPublic;
        this.savingVisibility.set(false);
        this.visibilitySuccess.set(true);
        this.editRaceModal.notifyUpdated();
        window.setTimeout(() => this.visibilitySuccess.set(false), 2500);
      },
      error: () => {
        this.isPublicInput = race.isPublic;
        this.visibilityFormError.set(this.i18n.t('race.visibilityUpdateError'));
        this.savingVisibility.set(false);
      },
    });
  }

  protected saveSchedule(): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || this.savingSchedule()) {
      return;
    }

    this.clearScheduleValidation();

    const invalidFields = new Set<ScheduleInvalidField>();
    const fieldErrors: Partial<Record<ScheduleInvalidField, string>> = {};
    const requiredMessage = this.i18n.t('create.fieldRequired');

    if (!this.startDateInput.trim()) {
      invalidFields.add('startDate');
      fieldErrors.startDate = requiredMessage;
    }

    if (!this.endDateInput.trim()) {
      invalidFields.add('endDate');
      fieldErrors.endDate = requiredMessage;
    }

    if (invalidFields.size > 0) {
      this.scheduleInvalidFields.set(invalidFields);
      this.scheduleFieldErrors.set(fieldErrors);
      this.scheduleFormError.set(this.i18n.t('create.formIncomplete'));
      return;
    }

    const startAt = buildLocalStartAtIso(this.startDateInput, this.startHourInput);
    if (!startAt) {
      this.scheduleInvalidFields.set(new Set(['startDate']));
      this.scheduleFieldErrors.set({ startDate: this.i18n.t('create.invalidStartDate') });
      this.scheduleFormError.set(this.i18n.t('create.invalidStartDate'));
      return;
    }

    const endAt = buildLocalStartAtIso(this.endDateInput, this.endHourInput);
    if (!endAt) {
      this.scheduleInvalidFields.set(new Set(['endDate']));
      this.scheduleFieldErrors.set({ endDate: this.i18n.t('create.invalidEndDate') });
      this.scheduleFormError.set(this.i18n.t('create.invalidEndDate'));
      return;
    }
    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      this.scheduleInvalidFields.set(new Set(['endDate']));
      this.scheduleFieldErrors.set({ endDate: this.i18n.t('create.endBeforeStart') });
      this.scheduleFormError.set(this.i18n.t('create.endBeforeStart'));
      return;
    }

    this.scheduleSuccess.set(false);
    this.savingSchedule.set(true);

    this.raceApi.updateRaceSchedule(race.id, { startAt, endAt }).subscribe({
      next: (updated) => {
        const normalized = normalizeRaceDetail(updated);
        this.editRaceModal.race.set(normalized);
        this.syncScheduleInputs(normalized);
        this.savingSchedule.set(false);
        this.scheduleSuccess.set(true);
        this.editRaceModal.notifyUpdated();
        window.setTimeout(() => this.scheduleSuccess.set(false), 2500);
      },
      error: () => {
        this.scheduleFormError.set(this.i18n.t('race.scheduleUpdateError'));
        this.savingSchedule.set(false);
      },
    });
  }

  protected addParticipant(): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || race.type !== 'SOLOQ') {
      return;
    }

    this.gameNameInput = normalizeGameName(this.gameNameInput);
    this.tagLineInput = normalizeTagLine(this.tagLineInput);

    if (!this.validateSoloParticipantFields()) {
      return;
    }

    const riotId = buildRiotId(this.gameNameInput, this.tagLineInput);
    if (!riotId) {
      this.participantInvalidFields.set(new Set(['gameName', 'tagLine']));
      this.participantFieldErrors.set({
        gameName: this.i18n.t('create.fieldRequired'),
        tagLine: this.i18n.t('create.fieldRequired'),
      });
      this.participantFormError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    this.addingParticipant.set(true);

    this.raceApi.addParticipant(race.id, { riotId }).subscribe({
      next: () => {
        this.resetParticipantInputs();
        this.clearParticipantValidation();
        this.addingParticipant.set(false);
        void this.reloadRace();
      },
      error: (err: HttpErrorResponse) => {
        this.participantFormError.set(mapParticipantError(err, this.i18n));
        this.addingParticipant.set(false);
      },
    });
  }

  protected addDuo(): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || race.type !== 'DUOQ') {
      return;
    }

    this.duoPlayer1GameName = normalizeGameName(this.duoPlayer1GameName);
    this.duoPlayer1TagLine = normalizeTagLine(this.duoPlayer1TagLine);
    this.duoPlayer2GameName = normalizeGameName(this.duoPlayer2GameName);
    this.duoPlayer2TagLine = normalizeTagLine(this.duoPlayer2TagLine);

    if (!this.validateDuoParticipantFields()) {
      return;
    }

    const player1RiotId = buildRiotId(this.duoPlayer1GameName, this.duoPlayer1TagLine);
    const player2RiotId = buildRiotId(this.duoPlayer2GameName, this.duoPlayer2TagLine);
    if (!player1RiotId || !player2RiotId) {
      this.participantFormError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    this.addingParticipant.set(true);

    this.raceApi.addDuo(race.id, { player1RiotId, player2RiotId }).subscribe({
      next: () => {
        this.resetParticipantInputs();
        this.clearParticipantValidation();
        this.addingParticipant.set(false);
        void this.reloadRace();
      },
      error: (err: HttpErrorResponse) => {
        this.participantFormError.set(mapParticipantError(err, this.i18n));
        this.addingParticipant.set(false);
      },
    });
  }

  protected removeParticipant(participant: ParticipantProgress): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || this.removingParticipantId()) {
      return;
    }

    this.participantFormError.set(null);
    this.removingParticipantId.set(participant.id);

    this.raceApi.removeParticipant(race.id, participant.id).subscribe({
      next: () => {
        this.removingParticipantId.set(null);
        void this.reloadRace();
      },
      error: () => {
        this.participantFormError.set(this.i18n.t('errors.removeParticipant'));
        this.removingParticipantId.set(null);
      },
    });
  }

  protected removeDuo(duo: DuoProgress): void {
    const race = this.editRaceModal.race();
    if (!race?.isOwner || this.removingDuoId()) {
      return;
    }

    this.participantFormError.set(null);
    this.removingDuoId.set(duo.id);

    this.raceApi.removeDuo(race.id, duo.id).subscribe({
      next: () => {
        this.removingDuoId.set(null);
        void this.reloadRace();
      },
      error: () => {
        this.participantFormError.set(this.i18n.t('errors.removeDuo'));
        this.removingDuoId.set(null);
      },
    });
  }

  private reloadRace(): void {
    const race = this.editRaceModal.race();
    if (!race) {
      return;
    }

    this.raceApi.getRaceByShareSlug(race.shareSlug).subscribe({
      next: (updated) => {
        const normalized = normalizeRaceDetail(updated);
        this.editRaceModal.race.set(normalized);
        this.syncScheduleInputs(normalized);
        this.nameInput = normalized.name;
        this.isPublicInput = normalized.isPublic;
        this.editRaceModal.notifyUpdated();
      },
    });
  }

  private syncScheduleInputs(race: RaceDetail): void {
    const startParts = splitLocalDateHour(race.startAt);
    if (!startParts) {
      this.startDateInput = '';
      this.startHourInput = 12;
    } else {
      this.startDateInput = startParts.date;
      this.startHourInput = startParts.hour;
    }

    const endParts = splitLocalDateHour(race.endAt);
    if (!endParts) {
      this.endDateInput = '';
      this.endHourInput = 12;
    } else {
      this.endDateInput = endParts.date;
      this.endHourInput = endParts.hour;
    }
  }

  private resetParticipantInputs(): void {
    this.gameNameInput = '';
    this.tagLineInput = '';
    this.duoPlayer1GameName = '';
    this.duoPlayer1TagLine = '';
    this.duoPlayer2GameName = '';
    this.duoPlayer2TagLine = '';
  }

  private clearScheduleValidation(): void {
    this.scheduleInvalidFields.set(new Set());
    this.scheduleFieldErrors.set({});
    this.scheduleFormError.set(null);
  }

  private clearNameValidation(): void {
    this.nameInvalidFields.set(new Set());
    this.nameFieldErrors.set({});
    this.nameFormError.set(null);
  }

  private clearParticipantValidation(): void {
    this.participantInvalidFields.set(new Set());
    this.participantFieldErrors.set({});
    this.participantFormError.set(null);
  }

  private validateSoloParticipantFields(): boolean {
    this.clearParticipantValidation();

    const invalidFields = new Set<ParticipantInvalidField>();
    const fieldErrors: Partial<Record<ParticipantInvalidField, string>> = {};
    const requiredMessage = this.i18n.t('create.fieldRequired');

    if (!this.gameNameInput.trim()) {
      invalidFields.add('gameName');
      fieldErrors.gameName = requiredMessage;
    }
    if (!this.tagLineInput.trim()) {
      invalidFields.add('tagLine');
      fieldErrors.tagLine = requiredMessage;
    }

    if (invalidFields.size > 0) {
      this.participantInvalidFields.set(invalidFields);
      this.participantFieldErrors.set(fieldErrors);
      this.participantFormError.set(this.i18n.t('create.formIncomplete'));
      return false;
    }

    return true;
  }

  private validateDuoParticipantFields(): boolean {
    this.clearParticipantValidation();

    const invalidFields = new Set<ParticipantInvalidField>();
    const fieldErrors: Partial<Record<ParticipantInvalidField, string>> = {};
    const requiredMessage = this.i18n.t('create.fieldRequired');

    const checks: [ParticipantInvalidField, string][] = [
      ['duoPlayer1GameName', this.duoPlayer1GameName],
      ['duoPlayer1TagLine', this.duoPlayer1TagLine],
      ['duoPlayer2GameName', this.duoPlayer2GameName],
      ['duoPlayer2TagLine', this.duoPlayer2TagLine],
    ];

    for (const [field, value] of checks) {
      if (!value.trim()) {
        invalidFields.add(field);
        fieldErrors[field] = requiredMessage;
      }
    }

    if (invalidFields.size > 0) {
      this.participantInvalidFields.set(invalidFields);
      this.participantFieldErrors.set(fieldErrors);
      this.participantFormError.set(this.i18n.t('create.formIncomplete'));
      return false;
    }

    return true;
  }
}
