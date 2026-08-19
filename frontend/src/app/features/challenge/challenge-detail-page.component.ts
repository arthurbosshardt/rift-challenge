import { Component, computed, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { EditChallengeModalService } from '../../core/services/edit-challenge-modal.service';
import { DeleteChallengeConfirmService } from '../../core/services/delete-challenge-confirm.service';
import { DuoProgress, ParticipantProgress, ChallengeDetail, ChallengeSummary } from '../../core/models/challenge.models';
import {
  LeaderboardSort,
  SortDirection,
  podiumTier,
  sortDuos,
  sortParticipants,
} from '../../core/utils/leaderboard-sort';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../core/utils/record-display';
import { formatDurationCountdown, formatFinishedRankLabel, formatRankLabel } from '../../core/utils/rank-display';
import { formatRefreshCountdown } from '../../core/utils/refresh-countdown';
import { formatTimeSince } from '../../core/utils/relative-time';
import { normalizeChallengeDetail } from '../../core/utils/challenge-detail';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ClampTooltipDirective } from '../../shared/directives/clamp-tooltip.directive';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { ChallengeDatePipe } from '../../shared/pipes/challenge-date.pipe';
import { ChallengeDetailSkeletonComponent } from '../../shared/components/challenge-detail-skeleton/challenge-detail-skeleton.component';
import { LeaderboardSkeletonComponent } from '../../shared/components/leaderboard-skeleton/leaderboard-skeleton.component';
import { LeaderboardSortControlsComponent } from '../../shared/components/leaderboard-sort-controls/leaderboard-sort-controls.component';
import { MatchHistoryStripComponent } from '../../shared/components/match-history-strip/match-history-strip.component';
import { GameDetailModalService } from '../../shared/services/game-detail-modal.service';
import {
  ChallengeBadgeComponent,
  challengeTypeBadgeKind,
} from '../../shared/components/challenge-badge/challenge-badge.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-challenge-detail-page',
  imports: [PageShellComponent, PlayerIdentityComponent, ChallengeDatePipe, LeaderboardSkeletonComponent, ChallengeDetailSkeletonComponent, LeaderboardSortControlsComponent, TranslatePipe, MatchHistoryStripComponent, ChallengeBadgeComponent, ClampTooltipDirective],
  templateUrl: './challenge-detail-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './challenge-detail-page.component.scss',
})
export class ChallengeDetailPageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly challengeApi = inject(ChallengeApiService);
  private readonly auth = inject(AuthService);
  private readonly editChallengeModal = inject(EditChallengeModalService);
  private readonly deleteChallengeConfirm = inject(DeleteChallengeConfirmService);
  private readonly gameDetailModal = inject(GameDetailModalService);
  private readonly i18n = inject(I18nService);

  private shareSlug = '';
  private countdownTimer: number | null = null;

  protected readonly challenge = signal<ChallengeDetail | null>(null);
  protected readonly loadingPreview = signal<ChallengeSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly refreshError = signal<string | null>(null);
  protected readonly refreshCountdown = signal<string | null>(null);
  protected readonly lastUpdateRelative = signal<string | null>(null);
  protected readonly startCountdown = signal<string | null>(null);
  protected readonly endCountdown = signal<string | null>(null);
  protected readonly sortCriterion = signal<LeaderboardSort>('RANK');
  protected readonly sortDirection = signal<SortDirection>('desc');

  protected readonly sortedParticipants = computed(() => {
    const currentChallenge = this.challenge();
    const criterion = this.sortCriterion();
    const direction = this.sortDirection();
    if (!currentChallenge) {
      return [];
    }
    return sortParticipants(currentChallenge.participants, criterion, direction);
  });

  protected readonly sortedDuos = computed(() => {
    const currentChallenge = this.challenge();
    const criterion = this.sortCriterion();
    const direction = this.sortDirection();
    if (!currentChallenge) {
      return [];
    }
    return sortDuos(currentChallenge.duos, criterion, direction);
  });

  ngOnInit(): void {
    this.shareSlug = this.route.snapshot.paramMap.get('shareSlug') ?? '';
    if (!this.shareSlug) {
      this.error.set(this.i18n.t('challenge.invalidLink'));
      this.loading.set(false);
      return;
    }

    this.loadingPreview.set(this.readNavigationPreview());
    void this.loadChallenge();
  }

  ngOnDestroy(): void {
    this.clearCountdown();
  }

  private readNavigationPreview(): ChallengeSummary | null {
    const state = history.state as { challengeSummary?: ChallengeSummary } | null;
    const summary = state?.challengeSummary;
    if (!summary || summary.shareSlug !== this.shareSlug) {
      return null;
    }
    return summary;
  }

  private async loadChallenge(): Promise<void> {
    await this.auth.waitUntilReady();

    this.challengeApi.getChallengeByShareSlug(this.shareSlug).subscribe({
      next: (challenge) => {
        const normalized = normalizeChallengeDetail(challenge);
        this.challenge.set(normalized);
        this.loading.set(false);
        this.startTimersIfNeeded();
        this.openEditIfRequested(normalized);
      },
      error: () => {
        this.error.set(this.i18n.t('challenge.notFound'));
        this.loading.set(false);
      },
    });
  }

  protected async copyShareLink(): Promise<void> {
    const challenge = this.challenge();
    if (!challenge) {
      return;
    }

    const path = challenge.sharePath || `/challenges/${encodeURIComponent(challenge.shareSlug)}`;
    const url = `${window.location.origin}${path}`;
    await navigator.clipboard.writeText(url);
    this.copied.set(true);
    window.setTimeout(() => this.copied.set(false), 2000);
  }

  protected openEditModal(challenge: ChallengeDetail): void {
    if (!challenge.isOwner) {
      return;
    }

    this.editChallengeModal.open(challenge, (result) => {
      this.applyChallengeUpdate(result.challenge);
      if (result.reloadFull) {
        this.reloadChallengeDetail(result.challenge.shareSlug);
      }
    });
  }

  private reloadChallengeDetail(shareSlug: string): void {
    if (this.refreshing()) {
      return;
    }

    this.refreshError.set(null);
    this.refreshing.set(true);

    this.challengeApi.getChallengeByShareSlug(shareSlug, true).subscribe({
      next: (challenge) => {
        const normalized = normalizeChallengeDetail(challenge);
        this.challenge.set(normalized);
        this.shareSlug = normalized.shareSlug;
        this.refreshing.set(false);
        this.startTimersIfNeeded();
      },
      error: () => {
        this.refreshing.set(false);
      },
    });
  }

  private applyChallengeUpdate(updated: ChallengeDetail): void {
    const normalized = normalizeChallengeDetail(updated);
    const slugChanged = updated.shareSlug !== this.shareSlug;

    this.challenge.set(normalized);
    this.shareSlug = updated.shareSlug;
    this.startTimersIfNeeded();

    if (slugChanged) {
      const path = updated.sharePath || `/challenges/${encodeURIComponent(updated.shareSlug)}`;
      void this.router.navigateByUrl(path, { replaceUrl: true });
    }
  }

  private openEditIfRequested(challenge: ChallengeDetail): void {
    if (this.route.snapshot.queryParamMap.get('edit') !== '1' || !challenge.isOwner) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { edit: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    this.openEditModal(challenge);
  }

  protected requestDeleteChallenge(challenge: ChallengeDetail): void {
    if (!challenge.isOwner || this.deleting()) {
      return;
    }

    this.deleteChallengeConfirm.open(challenge, (challengeId) => {
      this.performDeleteChallenge(challengeId);
    });
  }

    private performDeleteChallenge(challengeId: string): void {
    this.deleteError.set(null);
    this.deleting.set(true);

    this.challengeApi.deleteChallenge(challengeId).subscribe({
      next: () => {
        void this.router.navigate(['/my-challenges']);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.deleteError.set(this.i18n.t('errors.authRequired'));
        } else if (err.status === 404 || err.status === 405) {
          this.deleteError.set(this.i18n.t('challenge.deleteUnavailable'));
        } else {
          this.deleteError.set(this.i18n.t('challenge.deleteError'));
        }
        this.deleting.set(false);
      },
    });
  }

  protected setSortCriterion(criterion: LeaderboardSort): void {
    this.sortCriterion.set(criterion);
  }

  protected toggleSortDirection(): void {
    this.sortDirection.update((direction) => (direction === 'desc' ? 'asc' : 'desc'));
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

  protected readonly challengeTypeBadgeKind = challengeTypeBadgeKind;

  protected typeLabel(type: ChallengeDetail['type']): string {
    return type === 'SOLOQ' ? this.i18n.t('challenge.typeSoloq') : this.i18n.t('challenge.typeDuoq');
  }

  protected previewEntryLimit(type: ChallengeSummary['type']): number {
    return type === 'DUOQ' ? 8 : 16;
  }

  protected previewSkeletonRowCount(preview: ChallengeSummary): number {
    return preview.entryCount > 0 ? preview.entryCount : 4;
  }

  protected entryCount(challenge: ChallengeDetail): number {
    return challenge.type === 'DUOQ' ? challenge.duos.length : challenge.participants.length;
  }

  protected entryLimit(challenge: ChallengeDetail): number {
    return challenge.type === 'DUOQ' ? 8 : 16;
  }

  protected statsPanelSkeletonRowCount(challenge: ChallengeDetail): number {
    const count = this.entryCount(challenge);
    if (count === 0) {
      return 4;
    }
    return count;
  }

  protected hasLeaderboard(challenge: ChallengeDetail): boolean {
    if (challenge.status !== 'ACTIVE' && challenge.status !== 'FINISHED') {
      return false;
    }

    if (challenge.lastRefreshedAt) {
      return true;
    }

    if (challenge.type === 'SOLOQ') {
      return challenge.participants.some(
        (participant) => participant.hasRankData || participant.wins + participant.losses > 0,
      );
    }

    return challenge.duos.some(
      (duo) =>
        duo.player1.hasRankData ||
        duo.player2.hasRankData ||
        duo.wins + duo.losses > 0,
    );
  }

  protected refreshChallenge(): void {
    const challenge = this.challenge();
    if (!challenge?.refreshAvailable || this.refreshing()) {
      return;
    }

    this.refreshError.set(null);
    this.refreshing.set(true);

    this.challengeApi.refreshChallenge(challenge.id).subscribe({
      next: (updated) => {
        this.challenge.set(normalizeChallengeDetail(updated));
        this.refreshing.set(false);
        this.startTimersIfNeeded();
      },
      error: (err: HttpErrorResponse) => {
        this.refreshError.set(this.mapRefreshError(err));
        this.refreshing.set(false);
        if (err.status === 429) {
          void this.loadChallenge();
        }
      },
    });
  }

  protected participantRankLabel(participant: ParticipantProgress): string | null {
    if (!participant.hasRankData) {
      return null;
    }

    const locale = this.i18n.locale();
    if (this.challenge()?.status === 'FINISHED') {
      return formatFinishedRankLabel(
        participant.currentTier,
        participant.currentRank,
        participant.currentLp,
        locale,
      );
    }

    return formatRankLabel(
      participant.currentTier,
      participant.currentRank,
      participant.currentLp,
      locale,
      true,
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

  protected openParticipantGameDetail(matchId: string, participantId: string): void {
    const challengeId = this.challenge()?.id;
    if (!challengeId) {
      return;
    }
    this.gameDetailModal.open(matchId, { type: 'participant', challengeId, participantId });
  }

  protected openDuoGameDetail(matchId: string, duoId: string): void {
    const challengeId = this.challenge()?.id;
    if (!challengeId) {
      return;
    }
    this.gameDetailModal.open(matchId, { type: 'duo', challengeId, duoId });
  }

  protected ineligibilityLabel(reason: string | null | undefined): string {
    if (!reason) {
      return '';
    }
    const [code] = reason.split('|');
    if (code === 'SOLOQ_WITHOUT_PARTNER') {
      return this.i18n.t('duo.ineligibleSolo');
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
    const currentChallenge = this.challenge();
    if (!currentChallenge || currentChallenge.status !== 'NOT_STARTED') {
      this.startCountdown.set(null);
      return;
    }

    const countdown = formatDurationCountdown(currentChallenge.startAt, Date.now(), this.i18n.locale());
    if (!countdown) {
      this.startCountdown.set(null);
      this.clearCountdown();
      void this.loadChallenge();
      return;
    }

    this.startCountdown.set(countdown);
  }

  private updateEndCountdown(): void {
    const currentChallenge = this.challenge();
    if (!currentChallenge?.endAt || currentChallenge.status !== 'ACTIVE') {
      this.endCountdown.set(null);
      return;
    }

    const countdown = formatDurationCountdown(currentChallenge.endAt, Date.now(), this.i18n.locale());
    if (!countdown) {
      this.endCountdown.set(null);
      this.clearCountdown();
      void this.loadChallenge();
      return;
    }

    this.endCountdown.set(countdown);
  }

  private updateRefreshCountdown(): void {
    const currentChallenge = this.challenge();
    if (!currentChallenge || (currentChallenge.status !== 'ACTIVE' && currentChallenge.status !== 'FINISHED')) {
      this.refreshCountdown.set(null);
      return;
    }

    if (currentChallenge.refreshAvailable || !currentChallenge.nextRefreshAvailableAt) {
      this.refreshCountdown.set(null);
      return;
    }

    const countdown = formatRefreshCountdown(currentChallenge.nextRefreshAvailableAt);
    if (!countdown) {
      this.refreshCountdown.set(null);
      this.challenge.update((challenge) => (challenge ? { ...challenge, refreshAvailable: true } : challenge));
      return;
    }

    this.refreshCountdown.set(countdown);
  }

  private updateLastUpdateRelative(): void {
    const currentChallenge = this.challenge();
    if (!currentChallenge?.lastRefreshedAt) {
      this.lastUpdateRelative.set(null);
      return;
    }

    this.lastUpdateRelative.set(
      formatTimeSince(currentChallenge.lastRefreshedAt, Date.now(), this.i18n.locale()),
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
      return this.i18n.t('errors.challengeNotStarted');
    }
    if (err.status === 502 || err.status === 503) {
      return this.i18n.t('errors.riotUnavailable');
    }
    return this.i18n.t('errors.refreshFailed');
  }
}
