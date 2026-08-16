import { DatePipe } from '@angular/common';
import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { RaceApiService } from '../../core/services/race-api.service';
import { AuthService } from '../../core/services/auth.service';
import { ParticipantProgress, RaceDetail } from '../../core/models/race.models';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';

@Component({
  selector: 'app-race-detail-page',
  imports: [PageShellComponent, DatePipe, FormsModule],
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
  protected readonly removingParticipantId = signal<string | null>(null);

  protected riotIdInput = '';

  protected readonly participantLimit = 16;

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
        this.startCountdownIfNeeded(this.normalizeRace(race));
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

  protected addParticipant(): void {
    const race = this.race();
    if (!race?.isOwner) {
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
        this.startCountdownIfNeeded(this.normalizeRace(updated));
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
    if (!participant.hasRankData || !participant.currentTier) {
      return 'Non classé';
    }
    const division = participant.currentRank ? ` ${participant.currentRank}` : '';
    return `${participant.currentTier}${division} · ${participant.currentLp} LP`;
  }

  protected lpLabel(participant: ParticipantProgress): string {
    if (!participant.hasRankData) {
      return '—';
    }
    const prefix = participant.lpGained >= 0 ? '+' : '';
    return `${prefix}${participant.lpGained} LP`;
  }

  private normalizeRace(race: RaceDetail): RaceDetail {
    return {
      ...race,
      participants: race.participants ?? [],
      isOwner: race.isOwner ?? false,
      refreshAvailable: race.refreshAvailable ?? false,
    };
  }

  private startCountdownIfNeeded(race: RaceDetail): void {
    this.clearCountdown();

    if (race.refreshAvailable || !race.nextRefreshAvailableAt) {
      this.refreshCountdown.set(null);
      return;
    }

    const update = (): void => {
      const nextRefreshAvailableAt = this.race()?.nextRefreshAvailableAt;
      if (!nextRefreshAvailableAt) {
        this.refreshCountdown.set(null);
        this.clearCountdown();
        return;
      }

      const countdown = formatRefreshCountdown(nextRefreshAvailableAt);
      if (!countdown) {
        this.refreshCountdown.set(null);
        this.race.update((current) => (current ? { ...current, refreshAvailable: true } : current));
        this.clearCountdown();
        return;
      }

      this.refreshCountdown.set(countdown);
    };

    update();
    this.countdownTimer = window.setInterval(update, 1000);
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
    if (err.status === 400 && message.includes('limit')) {
      return 'Limite de 16 participants atteinte.';
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
