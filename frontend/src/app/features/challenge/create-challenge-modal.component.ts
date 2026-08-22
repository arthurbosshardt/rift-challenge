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
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { CreateChallengeModalService } from '../../core/services/create-challenge-modal.service';
import { SummonerSearchService, SummonerSuggestion } from '../../core/services/summoner-search.service';
import { ChallengeRegion, ChallengeType, DuoProgress, ParticipantProgress } from '../../core/models/challenge.models';
import { splitLocalDateHour } from '../../core/utils/challenge-date';
import {
  ChallengeEndMode,
  ScheduleInvalidField,
  ScheduleValidationResult,
  validateChallengeSchedule,
} from '../../core/utils/challenge-schedule';
import { mapParticipantError } from '../../core/utils/challenge-participant-errors';
import { isChallengeNameTakenError } from '../../core/utils/challenge-name-errors';
import { buildRiotId, parseRiotIdForLocale } from '../../core/utils/riot-id';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { SummonerTypeaheadComponent } from '../../shared/components/summoner-typeahead/summoner-typeahead.component';
import { LoaderComponent } from '../../shared/components/loader/loader.component';

type NameInvalidField = 'name';
type ParticipantInvalidField = 'riotId' | 'duoPlayer1RiotId' | 'duoPlayer2RiotId';
type EndMode = ChallengeEndMode;

@Component({
  selector: 'app-create-challenge-modal',
  imports: [
    FormsModule,
    TranslatePipe,
    PlayerIdentityComponent,
    SummonerTypeaheadComponent,
    LoaderComponent,
  ],
  templateUrl: './create-challenge-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './create-challenge-modal.component.scss',
})
export class CreateChallengeModalComponent {
  protected readonly createChallengeModal = inject(CreateChallengeModalService);
  private readonly challengeApi = inject(ChallengeApiService);
  private readonly summonerSearch = inject(SummonerSearchService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected nameInput = '';
  protected type: ChallengeType = 'SOLOQ';
  protected region: ChallengeRegion = 'EUW';
  protected readonly regionOptions: ChallengeRegion[] = ['EUW', 'EUNE', 'NA', 'KR'];
  protected startDateInput = '';
  protected startHourInput = 12;
  protected endMode: EndMode = 'DATE';
  protected endDateInput = '';
  protected endHourInput = 12;
  protected maxGamesInput: number | null = null;
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);

  protected riotIdInput = '';
  protected duoPlayer1RiotIdInput = '';
  protected duoPlayer2RiotIdInput = '';

  protected readonly loading = signal(false);
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

  constructor() {
    effect(() => {
      if (this.createChallengeModal.isOpen()) {
        this.resetForm();
        document.body.style.overflow = 'hidden';
        return;
      }

      document.body.style.overflow = '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.createChallengeModal.isOpen() && !this.loading()) {
      this.close();
    }
  }

  protected close(): void {
    this.createChallengeModal.close();
  }

  protected entryCount(): number {
    return this.type === 'DUOQ' ? this.draftDuos().length : this.draftParticipants().length;
  }

  protected entryLimit(): number {
    return this.type === 'DUOQ' ? 8 : 16;
  }

  protected participantListColumns(): 1 | 2 | 3 {
    const count = this.entryCount();
    if (this.type === 'DUOQ') {
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

  protected participantListClass(): string {
    if (this.type === 'DUOQ') {
      return 'create-challenge-modal__list challenge-form__duo-list';
    }

    const columns = this.participantListColumns();
    return columns === 1
      ? 'create-challenge-modal__list'
      : `create-challenge-modal__list create-challenge-modal__list--cols-${columns}`;
  }

  protected duoLabel(duo: DuoProgress): string {
    return `${duo.player1.riotId} / ${duo.player2.riotId}`;
  }

  protected regionLabel(region: ChallengeRegion): string {
    switch (region) {
      case 'EUNE':
        return this.i18n.t('challenge.regionEune');
      case 'NA':
        return this.i18n.t('challenge.regionNa');
      case 'KR':
        return this.i18n.t('challenge.regionKr');
      default:
        return this.i18n.t('challenge.regionEuw');
    }
  }

  protected onTypeChange(): void {
    this.draftParticipants.set([]);
    this.draftDuos.set([]);
    this.resetParticipantInputs();
    this.clearParticipantValidation();
    this.participantFormError.set(null);
  }

  protected toggleType(): void {
    if (this.loading()) {
      return;
    }

    this.type = this.type === 'SOLOQ' ? 'DUOQ' : 'SOLOQ';
    this.onTypeChange();
  }

  protected toggleEndMode(): void {
    if (this.loading()) {
      return;
    }

    this.endMode = this.endMode === 'DATE' ? 'GAMES' : 'DATE';
    this.clearScheduleInvalid('endDate');
    this.clearScheduleInvalid('maxGames');
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

  protected addParticipant(): void {
    if (this.type !== 'SOLOQ') {
      return;
    }

    this.riotIdInput = this.riotIdInput.trim();

    if (!this.validateSoloParticipantFields()) {
      return;
    }

    const parsed = parseRiotIdForLocale(this.riotIdInput, this.i18n.locale());
    if (!parsed) {
      this.participantInvalidFields.set(new Set(['riotId']));
      this.participantFieldErrors.set({ riotId: this.i18n.t('errors.riotIdFormat') });
      this.participantFormError.set(this.i18n.t('errors.riotIdFormat'));
      return;
    }

    const riotId = buildRiotId(parsed.gameName, parsed.tagLine);
    if (!riotId) {
      this.participantInvalidFields.set(new Set(['riotId']));
      this.participantFieldErrors.set({ riotId: this.i18n.t('errors.riotIdRequired') });
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
    if (this.type !== 'DUOQ') {
      return;
    }

    this.duoPlayer1RiotIdInput = this.duoPlayer1RiotIdInput.trim();
    this.duoPlayer2RiotIdInput = this.duoPlayer2RiotIdInput.trim();

    if (!this.validateDuoParticipantFields()) {
      return;
    }

    const player1 = parseRiotIdForLocale(this.duoPlayer1RiotIdInput, this.i18n.locale());
    const player2 = parseRiotIdForLocale(this.duoPlayer2RiotIdInput, this.i18n.locale());
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
    if (this.loading()) {
      return;
    }

    this.participantFormError.set(null);
    this.draftParticipants.update((list) => list.filter((item) => item.id !== participant.id));
  }

  protected removeDuo(duo: DuoProgress): void {
    if (this.loading()) {
      return;
    }

    this.participantFormError.set(null);
    this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
  }

  protected async submit(): Promise<void> {
    if (this.loading()) {
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

    this.flushCurrentInputsToDraft();

    const participants = [...this.draftParticipants()];
    const duos = [...this.draftDuos()];

    this.loading.set(true);
    this.formError.set(null);

    try {
      await this.assertPendingSummonersExist(participants, duos);

      const challenge = await firstValueFrom(
        this.challengeApi.createChallenge({
          name: trimmedName,
          type: this.type,
          region: this.region,
          startAt: scheduleValidation.startAt,
          ...(scheduleValidation.endAt ? { endAt: scheduleValidation.endAt } : {}),
          ...(scheduleValidation.maxGames ? { maxGames: scheduleValidation.maxGames } : {}),
        }),
      );

      if (this.type === 'SOLOQ') {
        for (const participant of participants) {
          await firstValueFrom(this.challengeApi.addParticipant(challenge.id, { riotId: participant.riotId }));
        }
      } else {
        for (const duo of duos) {
          await firstValueFrom(
            this.challengeApi.addDuo(challenge.id, {
              player1RiotId: duo.player1.riotId,
              player2RiotId: duo.player2.riotId,
            }),
          );
        }
      }

      this.createChallengeModal.close();
      await this.router.navigate(['/challenges', challenge.shareSlug]);
    } catch (err) {
      if (err instanceof HttpErrorResponse && isChallengeNameTakenError(err)) {
        this.nameInvalidFields.set(new Set(['name']));
        this.nameFieldErrors.set({ name: this.i18n.t('create.nameTaken') });
        this.formError.set(this.i18n.t('create.nameTaken'));
      } else if (err instanceof HttpErrorResponse && this.applyRiotNotFound(err)) {
        this.formError.set(mapParticipantError(err, this.i18n));
      } else if (err instanceof HttpErrorResponse) {
        this.formError.set(mapParticipantError(err, this.i18n) || this.i18n.t('create.error'));
      } else {
        this.formError.set(this.i18n.t('create.error'));
      }
    } finally {
      this.loading.set(false);
    }
  }

  private flushCurrentInputsToDraft(): void {
    if (this.type === 'SOLOQ' && this.riotIdInput.trim()) {
      this.addParticipant();
    }
    if (this.type === 'DUOQ' && this.duoPlayer1RiotIdInput.trim() && this.duoPlayer2RiotIdInput.trim()) {
      this.addDuo();
    }
  }

  private async assertPendingSummonersExist(
    participants: ParticipantProgress[],
    duos: DuoProgress[],
  ): Promise<void> {
    if (this.type === 'SOLOQ') {
      for (const participant of participants) {
        try {
          await firstValueFrom(this.summonerSearch.resolve(participant.riotId));
        } catch (err) {
          this.draftParticipants.update((list) => list.filter((item) => item.id !== participant.id));
          this.riotIdInput = participant.riotId;
          this.participantInvalidFields.set(new Set(['riotId']));
          this.participantFieldErrors.set({ riotId: this.i18n.t('errors.riotNotFound') });
          throw err;
        }
      }
      return;
    }

    for (const duo of duos) {
      try {
        await firstValueFrom(this.summonerSearch.resolve(duo.player1.riotId));
      } catch (err) {
        this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
        this.duoPlayer1RiotIdInput = duo.player1.riotId;
        this.duoPlayer2RiotIdInput = duo.player2.riotId;
        this.participantInvalidFields.set(new Set(['duoPlayer1RiotId']));
        this.participantFieldErrors.set({ duoPlayer1RiotId: this.i18n.t('errors.riotNotFound') });
        throw err;
      }
      try {
        await firstValueFrom(this.summonerSearch.resolve(duo.player2.riotId));
      } catch (err) {
        this.draftDuos.update((list) => list.filter((item) => item.id !== duo.id));
        this.duoPlayer1RiotIdInput = duo.player1.riotId;
        this.duoPlayer2RiotIdInput = duo.player2.riotId;
        this.participantInvalidFields.set(new Set(['duoPlayer2RiotId']));
        this.participantFieldErrors.set({ duoPlayer2RiotId: this.i18n.t('errors.riotNotFound') });
        throw err;
      }
    }
  }

  private applyRiotNotFound(err: HttpErrorResponse): boolean {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';
    if (err.status !== 404 || !message.includes('Riot account')) {
      return false;
    }

    if (this.type === 'DUOQ' && message.includes('player 2')) {
      this.participantInvalidFields.set(new Set(['duoPlayer2RiotId']));
      this.participantFieldErrors.set({ duoPlayer2RiotId: this.i18n.t('errors.riotNotFound') });
      return true;
    }
    if (this.type === 'DUOQ' && message.includes('player 1')) {
      this.participantInvalidFields.set(new Set(['duoPlayer1RiotId']));
      this.participantFieldErrors.set({ duoPlayer1RiotId: this.i18n.t('errors.riotNotFound') });
      return true;
    }

    this.participantInvalidFields.set(new Set(['riotId']));
    this.participantFieldErrors.set({ riotId: this.i18n.t('errors.riotNotFound') });
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

  private validateSchedule(): ScheduleValidationResult {
    return validateChallengeSchedule({
      endMode: this.endMode,
      startDateInput: this.startDateInput,
      startHourInput: this.startHourInput,
      endDateInput: this.endDateInput,
      endHourInput: this.endHourInput,
      maxGamesInput: this.maxGamesInput,
      messages: {
        required: this.i18n.t('create.fieldRequired'),
        formIncomplete: this.i18n.t('create.formIncomplete'),
        invalidStartDate: this.i18n.t('create.invalidStartDate'),
        invalidEndDate: this.i18n.t('create.invalidEndDate'),
        invalidMaxGames: this.i18n.t('create.invalidMaxGames'),
        endBeforeStart: this.i18n.t('create.endBeforeStart'),
      },
    });
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

  private resetParticipantInputs(): void {
    this.riotIdInput = '';
    this.duoPlayer1RiotIdInput = '';
    this.duoPlayer2RiotIdInput = '';
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

  private resetForm(): void {
    this.nameInput = '';
    this.type = 'SOLOQ';
    this.region = 'EUW';
    const now = splitLocalDateHour(new Date().toISOString());
    this.startDateInput = now?.date ?? '';
    this.startHourInput = now?.hour ?? 12;
    this.endMode = 'DATE';
    this.endDateInput = '';
    this.endHourInput = 12;
    this.maxGamesInput = 30;
    this.draftParticipants.set([]);
    this.draftDuos.set([]);
    this.resetParticipantInputs();
    this.loading.set(false);
    this.clearValidation();
    this.clearParticipantValidation();
  }
}
