import { Component, computed, HostListener, inject, input, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DuoPreview,
  ParticipantPreview,
  RaceSummary,
} from '../../../core/models/race.models';
import { RaceDatePipe } from '../../pipes/race-date.pipe';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { formatRankLabel } from '../../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../../core/utils/record-display';
import { resolveRaceCardPreviewLimit } from '../../../core/utils/race-summary';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';

@Component({
  selector: 'app-race-card',
  imports: [RouterLink, RaceDatePipe, TranslatePipe, PlayerAvatarComponent, RankEmblemComponent],
  templateUrl: './race-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './race-card.component.scss',
})
export class RaceCardComponent implements OnInit {
  readonly race = input.required<RaceSummary>();
  readonly hidePublicBadge = input(false);
  private readonly i18n = inject(I18nService);

  protected readonly previewLimit = signal(resolveRaceCardPreviewLimit(window.innerWidth));
  protected readonly entryCount = computed(() => this.race().entryCount ?? 0);
  protected readonly previewParticipants = computed(() => this.race().previewParticipants ?? []);
  protected readonly previewDuos = computed(() => this.race().previewDuos ?? []);
  protected readonly displayedParticipants = computed(() =>
    this.previewParticipants().slice(0, this.previewLimit()),
  );
  protected readonly displayedDuos = computed(() => this.previewDuos().slice(0, this.previewLimit()));
  protected readonly hasPreview = computed(
    () => this.previewParticipants().length > 0 || this.previewDuos().length > 0,
  );
  protected readonly remainingCount = computed(() => {
    const limit = this.previewLimit();
    const shown =
      this.race().type === 'DUOQ'
        ? Math.min(this.previewDuos().length, limit)
        : Math.min(this.previewParticipants().length, limit);
    return Math.max(0, this.entryCount() - shown);
  });
  protected readonly showLeaderboardPreview = computed(
    () => this.race().status !== 'NOT_STARTED',
  );

  ngOnInit(): void {
    this.updatePreviewLimit();
  }

  @HostListener('window:resize')
  protected onWindowResize(): void {
    this.updatePreviewLimit();
  }

  private updatePreviewLimit(): void {
    this.previewLimit.set(resolveRaceCardPreviewLimit(window.innerWidth));
  }

  statusLabel(status: RaceSummary['status']): string {
    if (status === 'NOT_STARTED') {
      return this.i18n.t('race.statusNotStarted');
    }
    if (status === 'FINISHED') {
      return this.i18n.t('race.statusFinished');
    }
    return this.i18n.t('race.statusActive');
  }

  statusClass(status: RaceSummary['status']): string {
    if (status === 'NOT_STARTED') {
      return 'badge--status-not-started';
    }
    if (status === 'FINISHED') {
      return 'badge--status-finished';
    }
    return 'badge--status-active';
  }

  typeLabel(type: RaceSummary['type']): string {
    return type === 'SOLOQ' ? this.i18n.t('race.typeSoloq') : this.i18n.t('race.typeDuoq');
  }

  typeClass(type: RaceSummary['type']): string {
    return type === 'SOLOQ' ? 'badge--soloq' : 'badge--duoq';
  }

  entryCountLabel(): string {
    const count = this.entryCount();
    if (this.race().type === 'DUOQ') {
      return this.i18n.t('race.duoCount', { count });
    }
    return this.i18n.t('race.entryCount', { count });
  }

  previewTitle(): string {
    return this.showLeaderboardPreview()
      ? this.i18n.t('race.previewLeaderboard')
      : this.i18n.t('race.previewRegistered');
  }

  rankLabel(participant: ParticipantPreview): string | null {
    if (!participant.currentTier) {
      return null;
    }
    return formatRankLabel(
      participant.currentTier,
      participant.currentRank,
      participant.currentLp,
      this.i18n.locale(),
    );
  }

  lpLabel(value: number): string {
    if (value > 0) {
      return `+${value} LP`;
    }
    if (value < 0) {
      return `${value} LP`;
    }
    return '0 LP';
  }

  lpClass(value: number): string {
    if (value > 0) {
      return 'race-card__lp--positive';
    }
    if (value < 0) {
      return 'race-card__lp--negative';
    }
    return 'race-card__lp--neutral';
  }

  positionClass(position: number, eligible = true): string {
    if (!eligible || !this.showLeaderboardPreview()) {
      return '';
    }
    if (position === 1) {
      return 'race-card__position--gold';
    }
    if (position === 2) {
      return 'race-card__position--silver';
    }
    if (position === 3) {
      return 'race-card__position--bronze';
    }
    return '';
  }

  previewItemClass(position: number, eligible = true): string {
    if (!eligible || !this.showLeaderboardPreview()) {
      return '';
    }
    if (position === 1) {
      return 'race-card__preview-item--gold';
    }
    if (position === 2) {
      return 'race-card__preview-item--silver';
    }
    if (position === 3) {
      return 'race-card__preview-item--bronze';
    }
    return '';
  }

  duoPositionClass(duo: DuoPreview): string {
    return this.positionClass(duo.position, duo.eligible);
  }

  participantPositionClass(participant: ParticipantPreview): string {
    return this.positionClass(participant.position);
  }

  duoPreviewItemClass(duo: DuoPreview): string {
    return this.previewItemClass(duo.position, duo.eligible);
  }

  participantPreviewItemClass(participant: ParticipantPreview): string {
    return this.previewItemClass(participant.position);
  }

  protected winRateLabel = winRateLabel;

  protected hasRecord = hasPlayedRecord;

  protected winRateClass(winRate: number, wins: number, losses: number): string {
    return `race-card__winrate--${winRateToneModifier(winRate, wins, losses)}`;
  }
}
