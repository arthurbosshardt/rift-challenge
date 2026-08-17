import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { DuoProgress, ParticipantProgress, RaceDetail } from '../../core/models/race.models';
import {
  LEADERBOARD_SORT_LABELS,
  LeaderboardSort,
  SortDirection,
  sortDirectionArrow,
  sortDuos,
  sortParticipants,
  winRateLabel,
} from '../../core/utils/leaderboard-sort';
import { formatDurationCountdown, formatRankLabel } from '../../core/utils/rank-display';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { RaceDatePipe } from '../../shared/pipes/race-date.pipe';

@Component({
  selector: 'app-race-detail-page',
  imports: [PageShellComponent, FormsModule, PlayerAvatarComponent, RaceDatePipe],
  templateUrl: './race-detail-page.component.html',
  styleUrl: './race-detail-page.component.scss',
})
export class RaceDetailPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly raceApi = inject(RaceApiService);
  private readonly auth = inject(AuthService);

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
  protected readonly removingParticipantId = signal<string | null>(null);
  protected readonly removingDuoId = signal<string | null>(null);
  protected readonly sortCriterion = signal<LeaderboardSort>('RANK');
  protected readonly sortDirection = signal<SortDirection>('desc');

  protected readonly sortOptions = Object.entries(LEADERBOARD_SORT_LABELS) as [LeaderboardSort, string][];

  protected readonly sortedParticipants = computed(() => {
    const currentRace = this.race();
    if (!currentRace) {
      return [];
    }
    return sortParticipants(currentRace.participants, this.sortCriterion(), this.sortDirection());
  });

  protected readonly sortedDuos = computed(() => {
    const currentRace = this.race();
    if (!currentRace) {
      return [];
    }
    return sortDuos(currentRace.duos, this.sortCriterion(), this.sortDirection());
  });

  protected riotIdInput = '';
  protected duoPlayer1Input = '';
  protected duoPlayer2Input = '';

  ngOnInit(): void {
    this.shareSlug = this.route.snapshot.paramMap.get('shareSlug') ?? '';
    if (!this.shareSlug) {
      this.error.set('Lien de race invalide.');
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
        this.race.set(this.normalizeRace(race));
        this.loading.set(false);
        this.startTimersIfNeeded();
      },
      error: () => {
        this.error.set('Race introuvable.');
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

    const riotId = this.riotIdInput.trim();
    if (!riotId) {
      this.participantError.set('Saisissez un Riot ID (ex. Tanor#7154).');
      return;
    }

    this.participantError.set(null);
    this.addingParticipant.set(true);

    this.raceApi.addParticipant(race.id, { riotId }).subscribe({
      next: () => {
        this.riotIdInput = '';
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

    const player1RiotId = this.duoPlayer1Input.trim();
    const player2RiotId = this.duoPlayer2Input.trim();
    if (!player1RiotId || !player2RiotId) {
      this.participantError.set('Saisissez les deux Riot ID du duo.');
      return;
    }

    this.participantError.set(null);
    this.addingParticipant.set(true);

    this.raceApi.addDuo(race.id, { player1RiotId, player2RiotId }).subscribe({
      next: () => {
        this.duoPlayer1Input = '';
        this.duoPlayer2Input = '';
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
        this.participantError.set('Impossible de retirer ce participant.');
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
        this.participantError.set('Impossible de retirer ce duo.');
        this.removingDuoId.set(null);
      },
    });
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
    return status === 'NOT_STARTED' ? 'Pas encore commencée' : 'En cours';
  }

  protected rankLabel(participant: ParticipantProgress): string {
    if (!participant.hasRankData) {
      return 'Non classé en SoloQ';
    }
    return formatRankLabel(participant.currentTier, participant.currentRank, participant.currentLp);
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

  private normalizeRace(race: RaceDetail): RaceDetail {
    return {
      ...race,
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

  private startTimersIfNeeded(): void {
    this.clearCountdown();

    const update = (): void => {
      this.updateStartCountdown();
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

    const countdown = formatDurationCountdown(currentRace.startAt);
    if (!countdown) {
      this.startCountdown.set(null);
      this.clearCountdown();
      void this.loadRace();
      return;
    }

    this.startCountdown.set(countdown);
  }

  private updateRefreshCountdown(): void {
    const currentRace = this.race();
    if (!currentRace || currentRace.status !== 'ACTIVE') {
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
      return 'Compte Riot introuvable. Vérifiez le Riot ID.';
    }
    if (err.status === 409) {
      return 'Ce joueur est déjà inscrit.';
    }
    if (err.status === 400 && message.includes('Duo limit')) {
      return 'Limite de 8 duos atteinte.';
    }
    if (err.status === 400 && message.includes('Participant limit')) {
      return 'Limite de participants atteinte.';
    }
    if (err.status === 400 && message.includes('duo endpoint')) {
      return 'Ajoutez les joueurs par paires en DuoQ.';
    }
    if (err.status === 400 && message.includes('two different players')) {
      return 'Un duo doit contenir deux joueurs différents.';
    }
    if (err.status === 400 && message.includes('gameName#tagLine')) {
      return 'Format invalide. Utilisez gameName#tagLine (ex. Tanor#7154).';
    }
    if (err.status === 429) {
      return 'API Riot saturée. Réessayez dans un instant.';
    }

    return 'Impossible d\'ajouter ce participant.';
  }

  private mapRefreshError(err: HttpErrorResponse): string {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';

    if (err.status === 429) {
      if (message.includes('partial sync')) {
        return 'Actualisation partielle enregistrée. L\'API Riot limite les appels — réessayez dans 2 minutes.';
      }
      if (message.includes('Riot API rate limit')) {
        return 'API Riot saturée. Réessayez dans un instant.';
      }
      return 'Refresh indisponible. Attendez 2 minutes entre chaque actualisation.';
    }
    if (err.status === 400) {
      return 'La race n\'a pas encore commencé.';
    }
    if (err.status === 502 || err.status === 503) {
      return 'L\'API Riot est indisponible. Réessayez plus tard.';
    }
    return 'Impossible d\'actualiser les données Riot.';
  }
}
