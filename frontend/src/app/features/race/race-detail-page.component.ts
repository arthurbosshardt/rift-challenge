import { Component, computed, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { DuoProgress, ParticipantProgress, RaceDetail } from '../../core/models/race.models';
import {
  LeaderboardSort,
  SortDirection,
  sortDirectionArrow,
  sortDuos,
  sortParticipants,
  isLeaderboardLeader,
  winRateLabel,
} from '../../core/utils/leaderboard-sort';
import { formatDurationCountdown, formatRankLabel } from '../../core/utils/rank-display';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { buildLocalStartAtIso, splitLocalDateHour } from '../../core/utils/race-date';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { RaceDatePipe } from '../../shared/pipes/race-date.pipe';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { buildRiotId } from '../../core/utils/riot-id';

@Component({
  selector: 'app-race-detail-page',
  imports: [PageShellComponent, FormsModule, PlayerIdentityComponent, RaceDatePipe, LoaderComponent, TranslatePipe],
  templateUrl: './race-detail-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './race-detail-page.component.scss',
})
export class RaceDetailPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly raceApi = inject(RaceApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  private shareSlug = '';
  private countdownTimer: number | null = null;

  protected readonly race = signal<RaceDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly addingParticipant = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly participantError = signal<string | null>(null);
  protected readonly refreshError = signal<string | null>(null);
  protected readonly refreshCountdown = signal<string | null>(null);
  protected readonly startCountdown = signal<string | null>(null);
  protected readonly endCountdown = signal<string | null>(null);
  protected readonly editingSchedule = signal(false);
  protected readonly savingSchedule = signal(false);
  protected readonly scheduleError = signal<string | null>(null);
  protected readonly scheduleSuccess = signal(false);
  protected readonly removingParticipantId = signal<string | null>(null);
  protected readonly removingDuoId = signal<string | null>(null);
  protected readonly sortCriterion = signal<LeaderboardSort>('RANK');
  protected readonly sortDirection = signal<SortDirection>('desc');

  protected readonly sortOptions = computed(() => {
    this.i18n.locale();
    return [
      ['RANK', this.i18n.t('sort.rank')],
      ['LP_GAIN', this.i18n.t('sort.lp')],
      ['WIN_RATE', this.i18n.t('sort.winRate')],
    ] as [LeaderboardSort, string][];
  });

  protected readonly sortedParticipants = computed(() => {
    const currentRace = this.race();
    const criterion = this.sortCriterion();
    const direction = this.sortDirection();
    if (!currentRace) {
      return [];
    }
    return sortParticipants(currentRace.participants, criterion, direction);
  });

  protected readonly sortedDuos = computed(() => {
    const currentRace = this.race();
    const criterion = this.sortCriterion();
    const direction = this.sortDirection();
    if (!currentRace) {
      return [];
    }
    return sortDuos(currentRace.duos, criterion, direction);
  });

  protected gameNameInput = '';
  protected tagLineInput = '';
  protected duoPlayer1GameName = '';
  protected duoPlayer1TagLine = '';
  protected duoPlayer2GameName = '';
  protected duoPlayer2TagLine = '';
  protected endDateInput = '';
  protected endHourInput = 12;
  protected startDateInput = '';
  protected startHourInput = 12;
  protected readonly hourOptions = Array.from({ length: 24 }, (_, hour) => hour);

  ngOnInit(): void {
    this.shareSlug = this.route.snapshot.paramMap.get('shareSlug') ?? '';
    if (!this.shareSlug) {
      this.error.set(this.i18n.t('race.invalidLink'));
      this.loading.set(false);
      return;
    }

    void this.loadRace();
  }

  ngOnDestroy(): void {
    this.clearCountdown();
  }

  private async loadRace(): Promise<void> {
    await this.auth.waitUntilReady();

    this.raceApi.getRaceByShareSlug(this.shareSlug).subscribe({
      next: (race) => {
        const normalized = this.normalizeRace(race);
        this.race.set(normalized);
        this.syncScheduleInputs(normalized);
        this.loading.set(false);
        this.startTimersIfNeeded();
      },
      error: () => {
        this.error.set(this.i18n.t('race.notFound'));
        this.loading.set(false);
      },
    });
  }

  protected async copyShareLink(): Promise<void> {
    const race = this.race();
    if (!race) {
      return;
    }

    const url = `${window.location.origin}/races/${race.shareSlug}`;
    await navigator.clipboard.writeText(url);
    this.copied.set(true);
    window.setTimeout(() => this.copied.set(false), 2000);
  }

  protected startEditingSchedule(): void {
    const race = this.race();
    if (!race?.isOwner) {
      return;
    }
    this.syncScheduleInputs(race);
    this.scheduleError.set(null);
    this.scheduleSuccess.set(false);
    this.editingSchedule.set(true);
  }

  protected cancelEditingSchedule(): void {
    const race = this.race();
    if (race) {
      this.syncScheduleInputs(race);
    }
    this.editingSchedule.set(false);
    this.scheduleError.set(null);
  }

  protected saveSchedule(): void {
    const race = this.race();
    if (!race?.isOwner || this.savingSchedule()) {
      return;
    }

    const startAt = buildLocalStartAtIso(this.startDateInput, this.startHourInput);
    if (!startAt) {
      this.scheduleError.set(this.i18n.t('create.invalidStartDate'));
      return;
    }

    const endAt = buildLocalStartAtIso(this.endDateInput, this.endHourInput);
    if (!endAt) {
      this.scheduleError.set(this.i18n.t('create.invalidEndDate'));
      return;
    }
    if (new Date(endAt).getTime() <= new Date(startAt).getTime()) {
      this.scheduleError.set(this.i18n.t('create.endBeforeStart'));
      return;
    }

    this.scheduleError.set(null);
    this.scheduleSuccess.set(false);
    this.savingSchedule.set(true);
    const willHaveStarted =
      race.status !== 'NOT_STARTED' || new Date(startAt).getTime() <= Date.now();
    const shouldAutoRefresh = willHaveStarted && race.refreshAvailable;
    if (shouldAutoRefresh) {
      this.refreshError.set(null);
      this.refreshing.set(true);
    }

    this.raceApi.updateRaceSchedule(race.id, { startAt, endAt }).subscribe({
      next: (updated) => {
        const normalized = this.normalizeRace(updated);
        this.race.set(normalized);
        this.syncScheduleInputs(normalized);
        this.savingSchedule.set(false);
        this.refreshing.set(false);
        this.editingSchedule.set(false);
        this.scheduleSuccess.set(true);
        this.startTimersIfNeeded();
        window.setTimeout(() => this.scheduleSuccess.set(false), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.scheduleError.set(this.i18n.t('race.scheduleUpdateError'));
        this.savingSchedule.set(false);
        this.refreshing.set(false);
        if (shouldAutoRefresh && err.status === 429) {
          void this.loadRace();
        }
      },
    });
  }

  protected setSort(criterion: LeaderboardSort): void {
    if (this.sortCriterion() === criterion) {
      this.sortDirection.update((direction) => (direction === 'desc' ? 'asc' : 'desc'));
      return;
    }

    this.sortCriterion.set(criterion);
    this.sortDirection.set('desc');
  }

  protected sortArrow(criterion: LeaderboardSort): string | null {
    if (this.sortCriterion() !== criterion) {
      return null;
    }
    return sortDirectionArrow(this.sortDirection());
  }

  protected isLeader(index: number, eligible = true): boolean {
    return isLeaderboardLeader(index, this.sortDirection(), eligible);
  }

  protected leaderboardRowTrack(participantId: string): string {
    return `${this.sortCriterion()}:${this.sortDirection()}:${participantId}`;
  }

  protected raceTitle(race: RaceDetail): string {
    return `${this.typeLabel(race.type)} · ${race.name}`;
  }

  protected typeLabel(type: RaceDetail['type']): string {
    return type === 'SOLOQ' ? 'SoloQ' : 'DuoQ';
  }

  protected entryCount(race: RaceDetail): number {
    return race.type === 'DUOQ' ? race.duos.length : race.participants.length;
  }

  protected entryLimit(race: RaceDetail): number {
    return race.type === 'DUOQ' ? 8 : 16;
  }

  protected addParticipant(): void {
    const race = this.race();
    if (!race?.isOwner || race.type !== 'SOLOQ') {
      return;
    }

    const riotId = buildRiotId(this.gameNameInput, this.tagLineInput);
    if (!this.gameNameInput.trim()) {
      this.participantError.set(this.i18n.t('errors.gameNameRequired'));
      return;
    }
    if (!this.tagLineInput.trim()) {
      this.participantError.set(this.i18n.t('errors.tagLineRequired'));
      return;
    }
    if (!riotId) {
      this.participantError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    this.participantError.set(null);
    this.addingParticipant.set(true);

    this.raceApi.addParticipant(race.id, { riotId }).subscribe({
      next: () => {
        this.gameNameInput = '';
        this.tagLineInput = '';
        this.addingParticipant.set(false);
        void this.loadRace();
      },
      error: (err: HttpErrorResponse) => {
        this.participantError.set(this.mapParticipantError(err));
        this.addingParticipant.set(false);
      },
    });
  }

  protected addDuo(): void {
    const race = this.race();
    if (!race?.isOwner || race.type !== 'DUOQ') {
      return;
    }

    const player1RiotId = buildRiotId(this.duoPlayer1GameName, this.duoPlayer1TagLine);
    const player2RiotId = buildRiotId(this.duoPlayer2GameName, this.duoPlayer2TagLine);
    if (!player1RiotId || !player2RiotId) {
      this.participantError.set(this.i18n.t('errors.duoFieldsRequired'));
      return;
    }

    this.participantError.set(null);
    this.addingParticipant.set(true);

    this.raceApi.addDuo(race.id, { player1RiotId, player2RiotId }).subscribe({
      next: () => {
        this.duoPlayer1GameName = '';
        this.duoPlayer1TagLine = '';
        this.duoPlayer2GameName = '';
        this.duoPlayer2TagLine = '';
        this.addingParticipant.set(false);
        void this.loadRace();
      },
      error: (err: HttpErrorResponse) => {
        this.participantError.set(this.mapParticipantError(err));
        this.addingParticipant.set(false);
      },
    });
  }

  protected removeParticipant(participant: ParticipantProgress): void {
    const race = this.race();
    if (!race?.isOwner || this.removingParticipantId()) {
      return;
    }

    this.participantError.set(null);
    this.removingParticipantId.set(participant.id);

    this.raceApi.removeParticipant(race.id, participant.id).subscribe({
      next: () => {
        this.removingParticipantId.set(null);
        void this.loadRace();
      },
      error: () => {
        this.participantError.set(this.i18n.t('errors.removeParticipant'));
        this.removingParticipantId.set(null);
      },
    });
  }

  protected removeDuo(duo: DuoProgress): void {
    const race = this.race();
    if (!race?.isOwner || this.removingDuoId()) {
      return;
    }

    this.participantError.set(null);
    this.removingDuoId.set(duo.id);

    this.raceApi.removeDuo(race.id, duo.id).subscribe({
      next: () => {
        this.removingDuoId.set(null);
        void this.loadRace();
      },
      error: () => {
        this.participantError.set(this.i18n.t('errors.removeDuo'));
        this.removingDuoId.set(null);
      },
    });
  }

  protected hasLeaderboard(race: RaceDetail): boolean {
    if (race.status !== 'ACTIVE' && race.status !== 'FINISHED') {
      return false;
    }

    if (race.lastRefreshedAt) {
      return true;
    }

    if (race.type === 'SOLOQ') {
      return race.participants.some(
        (participant) => participant.hasRankData || participant.wins + participant.losses > 0,
      );
    }

    return race.duos.some(
      (duo) =>
        duo.player1.hasRankData ||
        duo.player2.hasRankData ||
        duo.wins + duo.losses > 0,
    );
  }

  protected refreshRace(): void {
    const race = this.race();
    if (!race?.refreshAvailable || this.refreshing()) {
      return;
    }

    this.refreshError.set(null);
    this.refreshing.set(true);

    this.raceApi.refreshRace(race.id).subscribe({
      next: (updated) => {
        this.race.set(this.normalizeRace(updated));
        this.refreshing.set(false);
        this.startTimersIfNeeded();
      },
      error: (err: HttpErrorResponse) => {
        this.refreshError.set(this.mapRefreshError(err));
        this.refreshing.set(false);
        if (err.status === 429) {
          void this.loadRace();
        }
      },
    });
  }

  protected statusLabel(status: RaceDetail['status']): string {
    if (status === 'NOT_STARTED') {
      return this.i18n.t('race.statusNotStarted');
    }
    if (status === 'FINISHED') {
      return this.i18n.t('race.statusFinished');
    }
    return this.i18n.t('race.statusActive');
  }

  protected rankLabel(participant: ParticipantProgress): string {
    if (!participant.hasRankData) {
      return this.i18n.t('race.unranked');
    }
    return formatRankLabel(
      participant.currentTier,
      participant.currentRank,
      participant.currentLp,
      this.i18n.locale(),
    );
  }

  protected participantRankLabel(participant: ParticipantProgress): string | null {
    const parts: string[] = [];

    if (participant.hasRankData) {
      parts.push(
        formatRankLabel(
          participant.currentTier,
          participant.currentRank,
          participant.currentLp,
          this.i18n.locale(),
        ),
      );
    }

    if (participant.wins + participant.losses > 0) {
      parts.push(this.i18n.t('race.winsLosses', { wins: participant.wins, losses: participant.losses }));
    }

    if (parts.length === 0) {
      return null;
    }

    return parts.join(' · ');
  }

  protected duoHasRankData(duo: DuoProgress): boolean {
    return duo.player1.hasRankData || duo.player2.hasRankData;
  }

  protected lpLabel(value: number, hasData = true): string {
    if (!hasData) {
      return '—';
    }
    const prefix = value >= 0 ? '+' : '';
    return `${prefix}${value} LP`;
  }

  protected winRateLabel(winRate: number, wins: number, losses: number): string {
    return winRateLabel(winRate, wins, losses);
  }

  protected duoLabel(duo: DuoProgress): string {
    return `${duo.player1.riotId} & ${duo.player2.riotId}`;
  }

  protected ineligibilityLabel(reason: string | null | undefined): string {
    if (!reason) {
      return '';
    }
    const [code, name] = reason.split('|');
    if (code === 'SOLOQ_WITHOUT_PARTNER' && name) {
      return this.i18n.t('duo.ineligibleSolo', { name });
    }
    return reason;
  }

  private normalizeRace(race: RaceDetail): RaceDetail {
    return {
      ...race,
      endAt: race.endAt ?? null,
      participants: (race.participants ?? []).map((participant) => ({
        ...participant,
        rankScore: participant.rankScore ?? 0,
        winRate: participant.winRate ?? 0,
        profileIconId: participant.profileIconId ?? null,
      })),
      duos: (race.duos ?? []).map((duo) => ({
        ...duo,
        winRate: duo.winRate ?? 0,
        player1: {
          ...duo.player1,
          profileIconId: duo.player1.profileIconId ?? null,
        },
        player2: {
          ...duo.player2,
          profileIconId: duo.player2.profileIconId ?? null,
        },
      })),
      isOwner: race.isOwner ?? false,
      refreshAvailable: race.refreshAvailable ?? false,
    };
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

  private startTimersIfNeeded(): void {
    this.clearCountdown();

    const update = (): void => {
      this.updateStartCountdown();
      this.updateEndCountdown();
      this.updateRefreshCountdown();
    };

    update();
    this.countdownTimer = window.setInterval(update, 1000);
  }

  private updateStartCountdown(): void {
    const currentRace = this.race();
    if (!currentRace || currentRace.status !== 'NOT_STARTED') {
      this.startCountdown.set(null);
      return;
    }

    const countdown = formatDurationCountdown(currentRace.startAt, Date.now(), this.i18n.locale());
    if (!countdown) {
      this.startCountdown.set(null);
      this.clearCountdown();
      void this.loadRace();
      return;
    }

    this.startCountdown.set(countdown);
  }

  private updateEndCountdown(): void {
    const currentRace = this.race();
    if (!currentRace?.endAt || currentRace.status !== 'ACTIVE') {
      this.endCountdown.set(null);
      return;
    }

    const countdown = formatDurationCountdown(currentRace.endAt, Date.now(), this.i18n.locale());
    if (!countdown) {
      this.endCountdown.set(null);
      this.clearCountdown();
      void this.loadRace();
      return;
    }

    this.endCountdown.set(countdown);
  }

  private updateRefreshCountdown(): void {
    const currentRace = this.race();
    if (!currentRace || (currentRace.status !== 'ACTIVE' && currentRace.status !== 'FINISHED')) {
      this.refreshCountdown.set(null);
      return;
    }

    if (currentRace.refreshAvailable || !currentRace.nextRefreshAvailableAt) {
      this.refreshCountdown.set(null);
      return;
    }

    const countdown = formatRefreshCountdown(currentRace.nextRefreshAvailableAt);
    if (!countdown) {
      this.refreshCountdown.set(null);
      this.race.update((race) => (race ? { ...race, refreshAvailable: true } : race));
      return;
    }

    this.refreshCountdown.set(countdown);
  }

  private clearCountdown(): void {
    if (this.countdownTimer !== null) {
      window.clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  private mapParticipantError(err: HttpErrorResponse): string {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';

    if (err.status === 404 && message.includes('Riot account')) {
      return this.i18n.t('errors.riotNotFound');
    }
    if (err.status === 409) {
      return this.i18n.t('errors.alreadyAdded');
    }
    if (err.status === 400 && message.includes('Duo limit')) {
      return this.i18n.t('errors.duoLimit');
    }
    if (err.status === 400 && message.includes('Participant limit')) {
      return this.i18n.t('errors.participantLimit');
    }
    if (err.status === 400 && message.includes('duo endpoint')) {
      return this.i18n.t('errors.useDuoEndpoint');
    }
    if (err.status === 400 && message.includes('two different players')) {
      return this.i18n.t('errors.duoDifferentPlayers');
    }
    if (err.status === 400 && message.includes('gameName#tagLine')) {
      return this.i18n.t('errors.riotIdFormat');
    }
    if (err.status === 429) {
      return this.i18n.t('errors.riotRateLimit');
    }
    if (err.status === 502 || err.status === 503 || message.includes('Riot API')) {
      return this.i18n.t('errors.riotUnavailable');
    }
    if (err.status === 401) {
      return this.i18n.t('errors.authRequired');
    }

    return this.i18n.t('errors.addParticipant');
  }

  private mapRefreshError(err: HttpErrorResponse): string {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';

    if (err.status === 429) {
      if (message.includes('partial sync')) {
        return this.i18n.t('errors.refreshPartial');
      }
      if (message.includes('Riot API rate limit')) {
        return this.i18n.t('errors.riotRateLimit');
      }
      return this.i18n.t('errors.refreshCooldown');
    }
    if (err.status === 400) {
      return this.i18n.t('errors.raceNotStarted');
    }
    if (err.status === 502 || err.status === 503) {
      return this.i18n.t('errors.riotUnavailable');
    }
    return this.i18n.t('errors.refreshFailed');
  }
}
