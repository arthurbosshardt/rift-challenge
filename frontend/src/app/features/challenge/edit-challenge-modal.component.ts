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
import { firstValueFrom } from 'rxjs';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { EditChallengeModalService } from '../../core/services/edit-challenge-modal.service';
import { DuoProgress, ParticipantProgress, ChallengeDetail } from '../../core/models/challenge.models';
import { buildLocalStartAtIso, splitLocalDateHour } from '../../core/utils/challenge-date';
import { normalizeChallengeDetail } from '../../core/utils/challenge-detail';
import { mapParticipantError } from '../../core/utils/challenge-participant-errors';
import { isChallengeNameTakenError, mapChallengeNameError } from '../../core/utils/challenge-name-errors';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import {
  ChallengeBadgeComponent,
  challengeTypeBadgeKind,
} from '../../shared/components/challenge-badge/challenge-badge.component';
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

type ScheduleValidationResult =
  | {
      valid: true;
      startAt: string;
      endAt: string;
    }
  | {
      valid: false;
      invalidFields: Set<ScheduleInvalidField>;
      fieldErrors: Partial<Record<ScheduleInvalidField, string>>;
      formError: string;
    };

@Component({
  selector: 'app-edit-challenge-modal',
  imports: [FormsModule, TranslatePipe, PlayerIdentityComponent, ChallengeBadgeComponent],
  templateUrl: './edit-challenge-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './edit-challenge-modal.component.scss',
})
export class EditChallengeModalComponent {
  protected readonly editChallengeModal = inject(EditChallengeModalService);
  private readonly challengeApi = inject(ChallengeApiService);
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

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly saveSuccess = signal(false);
  protected readonly nameInvalidFields = signal<ReadonlySet<NameInvalidField>>(new Set());
  protected readonly nameFieldErrors = signal<Partial<Record<NameInvalidField, string>>>({});
  protected readonly scheduleInvalidFields = signal<ReadonlySet<ScheduleInvalidField>>(new Set());
  protected readonly scheduleFieldErrors = signal<Partial<Record<ScheduleInvalidField, string>>>({});
  protected readonly addingParticipant = signal(false);
  protected readonly participantFormError = signal<string | null>(null);
  protected readonly participantInvalidFields = signal<ReadonlySet<ParticipantInvalidField>>(new Set());
  protected readonly participantFieldErrors = signal<Partial<Record<ParticipantInvalidField, string>>>({});
  protected readonly removingParticipantId = signal<string | null>(null);
  protected readonly removingDuoId = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.editChallengeModal.isOpen()) {
        const challenge = this.editChallengeModal.challenge();
        if (challenge) {
          this.syncScheduleInputs(challenge);
          this.nameInput = challenge.name;
          this.isPublicInput = challenge.isPublic;
          this.resetParticipantInputs();
          this.clearValidation();
          this.saveSuccess.set(false);
        }
        document.body.style.overflow = 'hidden';
        return;
      }

      document.body.style.overflow = '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.editChallengeModal.isOpen() && !this.saving() && !this.addingParticipant()) {
      this.close();
    }
  }

  protected close(): void {
    this.editChallengeModal.close();
  }

  protected readonly challengeTypeBadgeKind = challengeTypeBadgeKind;

  protected typeLabel(type: ChallengeDetail['type']): string {
    return type === 'SOLOQ' ? this.i18n.t('challenge.typeSoloq') : this.i18n.t('challenge.typeDuoq');
  }

  protected entryCount(challenge: ChallengeDetail): number {
    return challenge.type === 'DUOQ' ? challenge.duos.length : challenge.participants.length;
  }

  protected entryLimit(challenge: ChallengeDetail): number {
    return challenge.type === 'DUOQ' ? 8 : 16;
  }

  protected participantListColumns(challenge: ChallengeDetail): 1 | 2 | 3 {
    const count = this.entryCount(challenge);
    if (challenge.type === 'DUOQ') {
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

  protected participantListClass(challenge: ChallengeDetail): string {
    const columns = this.participantListColumns(challenge);
    return columns === 1
      ? 'edit-challenge-modal__list'
      : `edit-challenge-modal__list edit-challenge-modal__list--cols-${columns}`;
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

    if (nextFields.size === 0 && this.nameInvalidFields().size === 0) {
      this.formError.set(null);
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

    if (nextFields.size === 0 && this.scheduleInvalidFields().size === 0) {
      this.formError.set(null);
    }
  }

  protected toggleVisibility(): void {
    if (this.saving()) {
      return;
    }

    this.isPublicInput = !this.isPublicInput;
    this.formError.set(null);
    this.saveSuccess.set(false);
  }

  protected async saveChallenge(): Promise<void> {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || this.saving()) {
      return;
    }

    this.clearValidation();
    this.saveSuccess.set(false);

    const trimmedName = this.nameInput.trim();
    if (!trimmedName) {
      this.nameInvalidFields.set(new Set(['name']));
      this.nameFieldErrors.set({ name: this.i18n.t('create.fieldRequired') });
      this.formError.set(this.i18n.t('create.formIncomplete'));
      return;
    }

    const scheduleValidation = this.validateSchedule();
    if (!scheduleValidation.valid) {
      this.scheduleInvalidFields.set(scheduleValidation.invalidFields);
      this.scheduleFieldErrors.set(scheduleValidation.fieldErrors);
      this.formError.set(scheduleValidation.formError);
      return;
    }

    const nameChanged = trimmedName !== challenge.name;
    const visibilityChanged = this.isPublicInput !== challenge.isPublic;
    const scheduleChanged = this.hasScheduleChanges(challenge, scheduleValidation.startAt, scheduleValidation.endAt);

    if (!nameChanged && !visibilityChanged && !scheduleChanged) {
      this.close();
      return;
    }

    this.saving.set(true);
    this.formError.set(null);

    try {
      let updated = challenge;

      if (nameChanged) {
        updated = await firstValueFrom(this.challengeApi.updateChallengeName(updated.id, { name: trimmedName }));
      }

      if (scheduleChanged) {
        updated = await firstValueFrom(
          this.challengeApi.updateChallengeSchedule(updated.id, {
            startAt: scheduleValidation.startAt,
            endAt: scheduleValidation.endAt,
          }),
        );
      }

      if (visibilityChanged) {
        updated = await firstValueFrom(
          this.challengeApi.updateChallengeVisibility(updated.id, { isPublic: this.isPublicInput }),
        );
      }

      const normalized = normalizeChallengeDetail(updated);
      this.editChallengeModal.challenge.set(normalized);
      this.syncScheduleInputs(normalized);
      this.nameInput = normalized.name;
      this.isPublicInput = normalized.isPublic;
      this.saveSuccess.set(true);
      this.editChallengeModal.notifyUpdated();
      window.setTimeout(() => this.saveSuccess.set(false), 2500);
    } catch (err) {
      if (err instanceof HttpErrorResponse && isChallengeNameTakenError(err)) {
        this.nameInvalidFields.set(new Set(['name']));
        this.nameFieldErrors.set({ name: this.i18n.t('create.nameTaken') });
        this.formError.set(this.i18n.t('create.nameTaken'));
      } else if (err instanceof HttpErrorResponse && nameChanged) {
        this.formError.set(mapChallengeNameError(err, this.i18n));
      } else {
        this.formError.set(this.i18n.t('challenge.saveChangesError'));
      }
    } finally {
      this.saving.set(false);
    }
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

  protected addParticipant(): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || challenge.type !== 'SOLOQ') {
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

    this.challengeApi.addParticipant(challenge.id, { riotId }).subscribe({
      next: () => {
        this.resetParticipantInputs();
        this.clearParticipantValidation();
        this.addingParticipant.set(false);
        void this.reloadChallenge();
      },
      error: (err: HttpErrorResponse) => {
        this.participantFormError.set(mapParticipantError(err, this.i18n));
        this.addingParticipant.set(false);
      },
    });
  }

  protected addDuo(): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || challenge.type !== 'DUOQ') {
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

    this.challengeApi.addDuo(challenge.id, { player1RiotId, player2RiotId }).subscribe({
      next: () => {
        this.resetParticipantInputs();
        this.clearParticipantValidation();
        this.addingParticipant.set(false);
        void this.reloadChallenge();
      },
      error: (err: HttpErrorResponse) => {
        this.participantFormError.set(mapParticipantError(err, this.i18n));
        this.addingParticipant.set(false);
      },
    });
  }

  protected removeParticipant(participant: ParticipantProgress): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || this.removingParticipantId()) {
      return;
    }

    this.participantFormError.set(null);
    this.removingParticipantId.set(participant.id);

    this.challengeApi.removeParticipant(challenge.id, participant.id).subscribe({
      next: () => {
        this.removingParticipantId.set(null);
        void this.reloadChallenge();
      },
      error: () => {
        this.participantFormError.set(this.i18n.t('errors.removeParticipant'));
        this.removingParticipantId.set(null);
      },
    });
  }

  protected removeDuo(duo: DuoProgress): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || this.removingDuoId()) {
      return;
    }

    this.participantFormError.set(null);
    this.removingDuoId.set(duo.id);

    this.challengeApi.removeDuo(challenge.id, duo.id).subscribe({
      next: () => {
        this.removingDuoId.set(null);
        void this.reloadChallenge();
      },
      error: () => {
        this.participantFormError.set(this.i18n.t('errors.removeDuo'));
        this.removingDuoId.set(null);
      },
    });
  }

  private reloadChallenge(): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge) {
      return;
    }

    this.challengeApi.getChallengeByShareSlug(challenge.shareSlug).subscribe({
      next: (updated) => {
        const normalized = normalizeChallengeDetail(updated);
        this.editChallengeModal.challenge.set(normalized);
        this.syncScheduleInputs(normalized);
        this.nameInput = normalized.name;
        this.isPublicInput = normalized.isPublic;
        this.editChallengeModal.notifyUpdated();
      },
    });
  }

  private syncScheduleInputs(challenge: ChallengeDetail): void {
    const startParts = splitLocalDateHour(challenge.startAt);
    if (!startParts) {
      this.startDateInput = '';
      this.startHourInput = 12;
    } else {
      this.startDateInput = startParts.date;
      this.startHourInput = startParts.hour;
    }

    const endParts = splitLocalDateHour(challenge.endAt);
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

  private clearValidation(): void {
    this.nameInvalidFields.set(new Set());
    this.nameFieldErrors.set({});
    this.scheduleInvalidFields.set(new Set());
    this.scheduleFieldErrors.set({});
    this.formError.set(null);
  }

  private clearParticipantValidation(): void {
    this.participantInvalidFields.set(new Set());
    this.participantFieldErrors.set({});
    this.participantFormError.set(null);
  }

  private validateSchedule(): ScheduleValidationResult {
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
      return {
        valid: false,
        invalidFields,
        fieldErrors,
        formError: this.i18n.t('create.formIncomplete'),
      };
    }

    const startAt = buildLocalStartAtIso(this.startDateInput, this.startHourInput);
    if (!startAt) {
      return {
        valid: false,
        invalidFields: new Set(['startDate']),
        fieldErrors: { startDate: this.i18n.t('create.invalidStartDate') },
        formError: this.i18n.t('create.invalidStartDate'),
      };
    }

    const endAt = buildLocalStartAtIso(this.endDateInput, this.endHourInput);
    if (!endAt) {
      return {
        valid: false,
        invalidFields: new Set(['endDate']),
        fieldErrors: { endDate: this.i18n.t('create.invalidEndDate') },
        formError: this.i18n.t('create.invalidEndDate'),
      };
    }

    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      return {
        valid: false,
        invalidFields: new Set(['endDate']),
        fieldErrors: { endDate: this.i18n.t('create.endBeforeStart') },
        formError: this.i18n.t('create.endBeforeStart'),
      };
    }

    return { valid: true, startAt, endAt };
  }

  private hasScheduleChanges(challenge: ChallengeDetail, startAt: string, endAt: string): boolean {
    return challenge.startAt !== startAt || challenge.endAt !== endAt;
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
