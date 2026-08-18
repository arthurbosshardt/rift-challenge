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
import { firstValueFrom, timeout } from 'rxjs';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { EditChallengeModalService } from '../../core/services/edit-challenge-modal.service';
import { DuoProgress, ParticipantProgress, ChallengeDetail } from '../../core/models/challenge.models';
import { buildLocalStartAtIso, challengeInstantsEqual, splitLocalDateHour } from '../../core/utils/challenge-date';
import { normalizeChallengeDetail, mergeChallengeMetadataUpdate } from '../../core/utils/challenge-detail';
import { mapParticipantError } from '../../core/utils/challenge-participant-errors';
import { isChallengeNameTakenError, mapChallengeNameError } from '../../core/utils/challenge-name-errors';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { SummonerTypeaheadComponent } from '../../shared/components/summoner-typeahead/summoner-typeahead.component';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { SummonerSearchService, SummonerSuggestion } from '../../core/services/summoner-search.service';
import {
  ChallengeBadgeComponent,
  challengeTypeBadgeKind,
} from '../../shared/components/challenge-badge/challenge-badge.component';
import { ClampTooltipDirective } from '../../shared/directives/clamp-tooltip.directive';
import { buildRiotId, parseRiotId } from '../../core/utils/riot-id';

type ScheduleInvalidField = 'startDate' | 'endDate';
type NameInvalidField = 'name';

type ParticipantInvalidField = 'riotId' | 'duoPlayer1RiotId' | 'duoPlayer2RiotId';

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
  imports: [FormsModule, TranslatePipe, PlayerIdentityComponent, ChallengeBadgeComponent, SummonerTypeaheadComponent, LoaderComponent, ClampTooltipDirective],
  templateUrl: './edit-challenge-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './edit-challenge-modal.component.scss',
})
export class EditChallengeModalComponent {
  protected readonly editChallengeModal = inject(EditChallengeModalService);
  private readonly challengeApi = inject(ChallengeApiService);
  private readonly summonerSearch = inject(SummonerSearchService);
  private readonly i18n = inject(I18nService);

  protected startDateInput = '';
  protected startHourInput = 12;
  protected endDateInput = '';
  protected endHourInput = 12;
  protected nameInput = '';
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);

  protected riotIdInput = '';
  protected duoPlayer1RiotIdInput = '';
  protected duoPlayer2RiotIdInput = '';
  protected isPublicInput = false;

  protected readonly saving = signal(false);
  protected readonly blockingSave = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly nameInvalidFields = signal<ReadonlySet<NameInvalidField>>(new Set());
  protected readonly nameFieldErrors = signal<Partial<Record<NameInvalidField, string>>>({});
  protected readonly scheduleInvalidFields = signal<ReadonlySet<ScheduleInvalidField>>(new Set());
  protected readonly scheduleFieldErrors = signal<Partial<Record<ScheduleInvalidField, string>>>({});
  protected readonly participantFormError = signal<string | null>(null);
  protected readonly participantInvalidFields = signal<ReadonlySet<ParticipantInvalidField>>(new Set());
  protected readonly participantFieldErrors = signal<Partial<Record<ParticipantInvalidField, string>>>({});
  protected readonly draftParticipants = signal<ParticipantProgress[]>([]);
  protected readonly draftDuos = signal<DuoProgress[]>([]);
  private removedParticipantIds = new Set<string>();
  private removedDuoIds = new Set<string>();
  private hydratedChallengeId: string | null = null;

  constructor() {
    effect(() => {
      const isOpen = this.editChallengeModal.isOpen();
      if (!isOpen) {
        this.hydratedChallengeId = null;
        document.body.style.overflow = '';
        return;
      }

      const challenge = this.editChallengeModal.challenge();
      if (challenge && challenge.id !== this.hydratedChallengeId) {
        this.hydratedChallengeId = challenge.id;
        this.syncScheduleInputs(challenge);
        this.nameInput = challenge.name;
        this.isPublicInput = challenge.isPublic;
        this.resetParticipantInputs();
        this.hydrateDrafts(challenge);
        this.clearValidation();
      }

      document.body.style.overflow = 'hidden';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.editChallengeModal.isOpen() && !this.saving()) {
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
    return challenge.type === 'DUOQ' ? this.draftDuos().length : this.draftParticipants().length;
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
    if (challenge.type === 'DUOQ') {
      return 'edit-challenge-modal__list challenge-form__duo-list';
    }

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
  }

  protected async saveChallenge(): Promise<void> {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || this.saving()) {
      return;
    }

    this.clearValidation();

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
    const metadataChanged = nameChanged || visibilityChanged || scheduleChanged;

    const hasCommittedRosterChanges =
      this.removedParticipantIds.size > 0 ||
      this.removedDuoIds.size > 0 ||
      this.draftParticipants().some((item) => item.id.startsWith('pending-')) ||
      this.draftDuos().some((item) => item.id.startsWith('pending-'));

    if (!metadataChanged && !hasCommittedRosterChanges) {
      this.close();
      return;
    }

    if (metadataChanged && !hasCommittedRosterChanges) {
      await this.saveMetadataOnly(challenge, {
        trimmedName,
        nameChanged,
        visibilityChanged,
        scheduleChanged,
        scheduleValidation,
      });
      return;
    }

    this.flushCurrentInputsToDraft(challenge);

    const pendingParticipants = this.draftParticipants().filter((item) => item.id.startsWith('pending-'));
    const pendingDuos = this.draftDuos().filter((item) => item.id.startsWith('pending-'));
    const rosterMutation =
      this.removedParticipantIds.size > 0 ||
      this.removedDuoIds.size > 0 ||
      pendingParticipants.length > 0 ||
      pendingDuos.length > 0;

    if (!metadataChanged && !rosterMutation) {
      this.close();
      return;
    }

    this.saving.set(true);
    this.blockingSave.set(true);
    this.formError.set(null);

    try {
      await this.assertPendingSummonersExist(challenge, pendingParticipants, pendingDuos);

      let updated = challenge;

      if (metadataChanged) {
        updated = await firstValueFrom(
          this.challengeApi.updateChallenge(updated.id, {
            ...(nameChanged ? { name: trimmedName } : {}),
            ...(scheduleChanged
              ? { startAt: scheduleValidation.startAt, endAt: scheduleValidation.endAt }
              : {}),
            ...(visibilityChanged ? { isPublic: this.isPublicInput } : {}),
          }),
        );
      }

      for (const participantId of this.removedParticipantIds) {
        await firstValueFrom(this.challengeApi.removeParticipant(updated.id, participantId));
      }
      for (const duoId of this.removedDuoIds) {
        await firstValueFrom(this.challengeApi.removeDuo(updated.id, duoId));
      }
      for (const participant of pendingParticipants) {
        await firstValueFrom(this.challengeApi.addParticipant(updated.id, { riotId: participant.riotId }));
      }
      for (const duo of pendingDuos) {
        await firstValueFrom(
          this.challengeApi.addDuo(updated.id, {
            player1RiotId: duo.player1.riotId,
            player2RiotId: duo.player2.riotId,
          }),
        );
      }

      const previous = normalizeChallengeDetail(challenge);
      const normalized = mergeChallengeMetadataUpdate(previous, normalizeChallengeDetail(updated));
      this.editChallengeModal.notifyUpdated(normalized, true);
      this.close();
    } catch (err) {
      this.handleSaveError(err, challenge, nameChanged);
    } finally {
      this.saving.set(false);
      this.blockingSave.set(false);
    }
  }

  private async saveMetadataOnly(
    challenge: ChallengeDetail,
    options: {
      trimmedName: string;
      nameChanged: boolean;
      visibilityChanged: boolean;
      scheduleChanged: boolean;
      scheduleValidation: Extract<ScheduleValidationResult, { valid: true }>;
    },
  ): Promise<void> {
    this.saving.set(true);
    this.formError.set(null);

    try {
      const updated = await firstValueFrom(
        this.challengeApi.updateChallenge(challenge.id, {
          ...(options.nameChanged ? { name: options.trimmedName } : {}),
          ...(options.scheduleChanged
            ? {
                startAt: options.scheduleValidation.startAt,
                endAt: options.scheduleValidation.endAt,
              }
            : {}),
          ...(options.visibilityChanged ? { isPublic: this.isPublicInput } : {}),
        }).pipe(timeout(15_000)),
      );

      const normalized = mergeChallengeMetadataUpdate(
        normalizeChallengeDetail(challenge),
        normalizeChallengeDetail(updated),
      );
      this.editChallengeModal.notifyUpdated(normalized, true);
      this.close();
    } catch (err) {
      this.handleSaveError(err, challenge, options.nameChanged);
    } finally {
      this.saving.set(false);
    }
  }

  private handleSaveError(err: unknown, challenge: ChallengeDetail, nameChanged: boolean): void {
    if (err instanceof HttpErrorResponse && isChallengeNameTakenError(err)) {
      this.nameInvalidFields.set(new Set(['name']));
      this.nameFieldErrors.set({ name: this.i18n.t('create.nameTaken') });
      this.formError.set(this.i18n.t('create.nameTaken'));
    } else if (err instanceof HttpErrorResponse && this.applyRiotNotFound(err, challenge)) {
      this.formError.set(mapParticipantError(err, this.i18n));
    } else if (err instanceof HttpErrorResponse && nameChanged) {
      this.formError.set(mapChallengeNameError(err, this.i18n));
    } else if (err instanceof HttpErrorResponse) {
      this.formError.set(mapParticipantError(err, this.i18n));
    } else {
      this.formError.set(this.i18n.t('challenge.saveChangesError'));
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

    this.riotIdInput = this.riotIdInput.trim();

    if (!this.validateSoloParticipantFields()) {
      return;
    }

    const parsed = parseRiotId(this.riotIdInput);
    if (!parsed) {
      this.participantInvalidFields.set(new Set(['riotId']));
      this.participantFieldErrors.set({ riotId: this.i18n.t('errors.riotIdFormat') });
      this.participantFormError.set(this.i18n.t('errors.riotIdFormat'));
      return;
    }

    const riotId = buildRiotId(parsed.gameName, parsed.tagLine);
    if (!riotId) {
      this.participantInvalidFields.set(new Set(['riotId']));
      this.participantFieldErrors.set({
        riotId: this.i18n.t('errors.riotIdRequired'),
      });
      this.participantFormError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    if (this.draftParticipants().some((item) => item.riotId.toLowerCase() === riotId.toLowerCase())) {
      this.participantFormError.set(this.i18n.t('errors.alreadyAdded'));
      return;
    }

    this.draftParticipants.update((list) => [...list, this.toPendingParticipant(parsed.gameName, parsed.tagLine)]);
    this.resetParticipantInputs();
    this.clearParticipantValidation();
  }

  protected addDuo(): void {
    const challenge = this.editChallengeModal.challenge();
    if (!challenge?.isOwner || challenge.type !== 'DUOQ') {
      return;
    }

    this.duoPlayer1RiotIdInput = this.duoPlayer1RiotIdInput.trim();
    this.duoPlayer2RiotIdInput = this.duoPlayer2RiotIdInput.trim();

    if (!this.validateDuoParticipantFields()) {
      return;
    }

    const player1 = parseRiotId(this.duoPlayer1RiotIdInput);
    const player2 = parseRiotId(this.duoPlayer2RiotIdInput);
    if (!player1 || !player2) {
      const invalidFields = new Set<ParticipantInvalidField>();
      const fieldErrors: Partial<Record<ParticipantInvalidField, string>> = {};
      const formatError = this.i18n.t('errors.riotIdFormat');
      if (!player1) {
        invalidFields.add('duoPlayer1RiotId');
        fieldErrors.duoPlayer1RiotId = formatError;
      }
      if (!player2) {
        invalidFields.add('duoPlayer2RiotId');
        fieldErrors.duoPlayer2RiotId = formatError;
      }
      this.participantInvalidFields.set(invalidFields);
      this.participantFieldErrors.set(fieldErrors);
      this.participantFormError.set(formatError);
      return;
    }

    const player1RiotId = buildRiotId(player1.gameName, player1.tagLine);
    const player2RiotId = buildRiotId(player2.gameName, player2.tagLine);
    if (!player1RiotId || !player2RiotId) {
      this.participantFormError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    const existing = this.draftDuos().flatMap((duo) => [duo.player1.riotId, duo.player2.riotId]);
    if (
      existing.some((id) => id.toLowerCase() === player1RiotId.toLowerCase() || id.toLowerCase() === player2RiotId.toLowerCase())
    ) {
      this.participantFormError.set(this.i18n.t('errors.alreadyAdded'));
      return;
    }

    this.draftDuos.update((list) => [
      ...list,
      this.toPendingDuo(player1.gameName, player1.tagLine, player2.gameName, player2.tagLine),
    ]);
    this.resetParticipantInputs();
    this.clearParticipantValidation();
  }

  protected removeParticipant(participant: ParticipantProgress): void {
    if (this.saving()) {
      return;
    }

    this.participantFormError.set(null);
    if (participant.id.startsWith('pending-')) {
      this.draftParticipants.update((list) => list.filter((item) => item.id !== participant.id));
      return;
    }

    this.removedParticipantIds.add(participant.id);
    this.draftParticipants.update((list) => list.filter((item) => item.id !== participant.id));
  }

  protected removeDuo(duo: DuoProgress): void {
    if (this.saving()) {
      return;
    }

    this.participantFormError.set(null);
    if (duo.id.startsWith('pending-')) {
      this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
      return;
    }

    this.removedDuoIds.add(duo.id);
    this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
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
    this.riotIdInput = '';
    this.duoPlayer1RiotIdInput = '';
    this.duoPlayer2RiotIdInput = '';
  }

  private hydrateDrafts(challenge: ChallengeDetail): void {
    this.draftParticipants.set([...challenge.participants]);
    this.draftDuos.set([...challenge.duos]);
    this.removedParticipantIds = new Set();
    this.removedDuoIds = new Set();
  }

  protected applySummoner(target: 'solo' | 'duo1' | 'duo2', suggestion: SummonerSuggestion): void {
    if (target === 'solo') {
      this.riotIdInput = suggestion.riotId;
      this.clearParticipantInvalid('riotId');
      return;
    }
    if (target === 'duo1') {
      this.duoPlayer1RiotIdInput = suggestion.riotId;
      this.clearParticipantInvalid('duoPlayer1RiotId');
      return;
    }
    this.duoPlayer2RiotIdInput = suggestion.riotId;
    this.clearParticipantInvalid('duoPlayer2RiotId');
  }

  private flushCurrentInputsToDraft(challenge: ChallengeDetail): void {
    if (challenge.type === 'SOLOQ' && this.riotIdInput.trim()) {
      this.addParticipant();
    }
    if (
      challenge.type === 'DUOQ'
      && this.duoPlayer1RiotIdInput.trim()
      && this.duoPlayer2RiotIdInput.trim()
    ) {
      this.addDuo();
    }
  }

  private async assertPendingSummonersExist(
    challenge: ChallengeDetail,
    pendingParticipants: ParticipantProgress[],
    pendingDuos: DuoProgress[],
  ): Promise<void> {
    if (challenge.type === 'SOLOQ') {
      for (const participant of pendingParticipants) {
        try {
          await firstValueFrom(this.summonerSearch.resolve(participant.riotId));
        } catch (err) {
          this.draftParticipants.update((list) => list.filter((item) => item.id !== participant.id));
          this.riotIdInput = participant.riotId;
          this.participantInvalidFields.set(new Set(['riotId']));
          this.participantFieldErrors.set({
            riotId: this.i18n.t('errors.riotNotFound'),
          });
          throw err;
        }
      }
      return;
    }

    for (const duo of pendingDuos) {
      try {
        await firstValueFrom(this.summonerSearch.resolve(duo.player1.riotId));
      } catch (err) {
        this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
        this.duoPlayer1RiotIdInput = duo.player1.riotId;
        this.duoPlayer2RiotIdInput = duo.player2.riotId;
        this.participantInvalidFields.set(new Set(['duoPlayer1RiotId']));
        this.participantFieldErrors.set({
          duoPlayer1RiotId: this.i18n.t('errors.riotNotFound'),
        });
        throw err;
      }
      try {
        await firstValueFrom(this.summonerSearch.resolve(duo.player2.riotId));
      } catch (err) {
        this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
        this.duoPlayer1RiotIdInput = duo.player1.riotId;
        this.duoPlayer2RiotIdInput = duo.player2.riotId;
        this.participantInvalidFields.set(new Set(['duoPlayer2RiotId']));
        this.participantFieldErrors.set({
          duoPlayer2RiotId: this.i18n.t('errors.riotNotFound'),
        });
        throw err;
      }
    }
  }

  private applyRiotNotFound(err: HttpErrorResponse, challenge: ChallengeDetail): boolean {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';
    if (err.status !== 404 || !message.includes('Riot account')) {
      return false;
    }

    if (challenge.type === 'DUOQ' && message.includes('player 2')) {
      this.participantInvalidFields.set(new Set(['duoPlayer2RiotId']));
      this.participantFieldErrors.set({
        duoPlayer2RiotId: this.i18n.t('errors.riotNotFound'),
      });
      return true;
    }
    if (challenge.type === 'DUOQ' && message.includes('player 1')) {
      this.participantInvalidFields.set(new Set(['duoPlayer1RiotId']));
      this.participantFieldErrors.set({
        duoPlayer1RiotId: this.i18n.t('errors.riotNotFound'),
      });
      return true;
    }

    this.participantInvalidFields.set(new Set(['riotId']));
    this.participantFieldErrors.set({
      riotId: this.i18n.t('errors.riotNotFound'),
    });
    return true;
  }

  private toPendingParticipant(gameName: string, tagLine: string): ParticipantProgress {
    return {
      id: `pending-${crypto.randomUUID()}`,
      gameName,
      tagLine,
      riotId: `${gameName}#${tagLine}`,
      position: 0,
      currentTier: null,
      currentRank: null,
      currentLp: 0,
      lpGained: 0,
      rankScore: 0,
      wins: 0,
      losses: 0,
      winRate: 0,
      profileIconId: null,
      hasRankData: false,
    rankEstimated: false,
      recentMatches: [],
    };
  }

  private toPendingDuo(
    player1GameName: string,
    player1TagLine: string,
    player2GameName: string,
    player2TagLine: string,
  ): DuoProgress {
    return {
      id: `pending-${crypto.randomUUID()}`,
      player1: this.toPendingParticipant(player1GameName, player1TagLine),
      player2: this.toPendingParticipant(player2GameName, player2TagLine),
      combinedRankScore: 0,
      combinedLpGained: 0,
      wins: 0,
      losses: 0,
      winRate: 0,
      eligible: true,
      ineligibilityReason: null,
      position: 0,
      recentMatches: [],
    };
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
    return !challengeInstantsEqual(challenge.startAt, startAt)
      || !challengeInstantsEqual(challenge.endAt, endAt);
  }

  private validateSoloParticipantFields(): boolean {
    this.clearParticipantValidation();

    if (!this.riotIdInput.trim()) {
      this.participantInvalidFields.set(new Set(['riotId']));
      this.participantFieldErrors.set({ riotId: this.i18n.t('create.fieldRequired') });
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
      ['duoPlayer1RiotId', this.duoPlayer1RiotIdInput],
      ['duoPlayer2RiotId', this.duoPlayer2RiotIdInput],
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
