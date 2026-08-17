import { Component, computed, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { EditRaceModalService } from '../../core/services/edit-race-modal.service';
import { DuoProgress, ParticipantProgress, RaceDetail } from '../../core/models/race.models';
import {
  LeaderboardSort,
  SortDirection,
  podiumTier,
  sortDirectionArrow,
  sortDuos,
  sortParticipants,
} from '../../core/utils/leaderboard-sort';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../core/utils/record-display';
import { formatDurationCountdown, formatRankLabel } from '../../core/utils/rank-display';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { formatTimeSince } from '../../core/utils/relative-time';
import { normalizeRaceDetail } from '../../core/utils/race-detail';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { RaceDatePipe } from '../../shared/pipes/race-date.pipe';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-race-detail-page',
  imports: [PageShellComponent, PlayerIdentityComponent, RaceDatePipe, LoaderComponent, TranslatePipe],
  templateUrl: './race-detail-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './race-detail-page.component.scss',
})
export class RaceDetailPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly raceApi = inject(RaceApiService);
  private readonly auth = inject(AuthService);
  private readonly editRaceModal = inject(EditRaceModalService);
  private readonly i18n = inject(I18nService);

  private shareSlug = '';
  private countdownTimer: number | null = null;

  protected readonly race = signal<RaceDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly refreshError = signal<string | null>(null);
  protected readonly refreshCountdown = signal<string | null>(null);
  protected readonly lastUpdateRelative = signal<string | null>(null);
  protected readonly startCountdown = signal<string | null>(null);
  protected readonly endCountdown = signal<string | null>(null);
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
        const normalized = normalizeRaceDetail(race);
        this.race.set(normalized);
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

  protected openEditModal(race: RaceDetail): void {
    if (!race.isOwner) {
      return;
    }

    this.editRaceModal.open(race, () => {
      void this.loadRace();
    });
  }

  protected setSortCriterion(criterion: LeaderboardSort): void {
    this.sortCriterion.set(criterion);
  }

  protected toggleSortDirection(): void {
    this.sortDirection.update((direction) => (direction === 'desc' ? 'asc' : 'desc'));
  }

  protected sortDirectionAriaLabel(): string {
    const direction = this.sortDirection() === 'desc' ? this.i18n.t('sort.desc') : this.i18n.t('sort.asc');
    return `${this.i18n.t('sort.directionAria')}: ${direction}`;
  }

  protected sortDirectionIcon(): string {
    return sortDirectionArrow(this.sortDirection());
  }

  protected podiumTierForParticipant(position: number): string | null {
    const tier = podiumTier(position, this.sortedParticipants().length);
    return tier ? `leaderboard__item--podium-${tier}` : null;
  }

  protected podiumTierForDuo(duo: DuoProgress): string | null {
    const tier = podiumTier(duo.position, this.sortedDuos().length, duo.eligible);
    return tier ? `leaderboard__item--podium-${tier}` : null;
  }

  protected leaderboardRowTrack(participantId: string): string {
    return `${this.sortCriterion()}:${this.sortDirection()}:${participantId}`;
  }

  protected raceTitle(race: RaceDetail): string {
    return `${this.typeLabel(race.type)} · ${race.name}`;
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
        this.race.set(normalizeRaceDetail(updated));
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

  protected participantRankLabel(participant: ParticipantProgress): string | null {
    if (!participant.hasRankData) {
      return null;
    }

    return formatRankLabel(
      participant.currentTier,
      participant.currentRank,
      participant.currentLp,
      this.i18n.locale(),
    );
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

  protected winRateLabel = winRateLabel;

  protected winRateClass(winRate: number, wins: number, losses: number): string {
    return `leaderboard__winrate--${winRateToneModifier(winRate, wins, losses)}`;
  }

  protected hasRecord = hasPlayedRecord;

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

  private startTimersIfNeeded(): void {
    this.clearCountdown();

    const update = (): void => {
      this.updateStartCountdown();
      this.updateEndCountdown();
      this.updateRefreshCountdown();
      this.updateLastUpdateRelative();
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

  private updateLastUpdateRelative(): void {
    const currentRace = this.race();
    if (!currentRace?.lastRefreshedAt) {
      this.lastUpdateRelative.set(null);
      return;
    }

    this.lastUpdateRelative.set(
      formatTimeSince(currentRace.lastRefreshedAt, Date.now(), this.i18n.locale()),
    );
  }

  private clearCountdown(): void {
    if (this.countdownTimer !== null) {
      window.clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
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
