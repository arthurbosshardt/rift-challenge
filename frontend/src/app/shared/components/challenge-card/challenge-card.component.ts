import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DuoPreview,
  ParticipantPreview,
  ChallengeSummary,
} from '../../../core/models/challenge.models';
import { ChallengeDatePipe } from '../../pipes/challenge-date.pipe';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { formatFinishedRankLabel, formatRankLabel } from '../../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../../core/utils/record-display';
import {
  groupChallengeCardPreviewItems,
  resolveChallengeCardPreviewColumnCount,
  resolveChallengeCardPreviewVisibleCount,
} from '../../../core/utils/challenge-card-preview-grid';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';
import { MedalBadgeComponent } from '../medal-badge/medal-badge.component';
import { formatChallengeDateCompact } from '../../../core/utils/challenge-date';
import {
  ChallengeBadgeComponent,
  challengeRegionBadgeKind,
  challengeStatusBadgeKind,
  challengeTypeBadgeKind,
} from '../challenge-badge/challenge-badge.component';

import { ClampTooltipDirective } from '../../directives/clamp-tooltip.directive';
import { HideIfTruncatedDirective } from '../../directives/hide-if-truncated.directive';

@Component({
  selector: 'app-challenge-card',
  imports: [RouterLink, ChallengeDatePipe, TranslatePipe, PlayerAvatarComponent, RankEmblemComponent, MedalBadgeComponent, ChallengeBadgeComponent, ClampTooltipDirective, HideIfTruncatedDirective],
  templateUrl: './challenge-card.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './challenge-card.component.scss',
})
export class ChallengeCardComponent implements AfterViewInit, OnDestroy {
  readonly challenge = input.required<ChallengeSummary>();
  readonly hidePreview = input(false);
  private readonly i18n = inject(I18nService);
  private readonly previewViewport = viewChild<ElementRef<HTMLElement>>('previewViewport');
  private readonly previewTrack = viewChild<ElementRef<HTMLElement>>('previewTrack');
  private resizeObserver: ResizeObserver | null = null;

  protected readonly showPreviewFade = signal(false);
  protected readonly previewViewportWidth = signal(0);
  protected readonly entryCount = computed(() => this.challenge().entryCount ?? 0);
  protected readonly previewParticipants = computed(() => this.challenge().previewParticipants ?? []);
  protected readonly previewDuos = computed(() => this.challenge().previewDuos ?? []);
  protected readonly previewEntryCount = computed(() =>
    this.challenge().type === 'DUOQ' ? this.previewDuos().length : this.previewParticipants().length,
  );
  protected readonly visiblePreviewCount = computed(() =>
    resolveChallengeCardPreviewVisibleCount(this.previewEntryCount()),
  );
  protected readonly previewColumnCount = computed(() =>
    resolveChallengeCardPreviewColumnCount(this.previewViewportWidth(), this.visiblePreviewCount()),
  );
  protected readonly visiblePreviewParticipants = computed(() =>
    this.previewParticipants().slice(0, this.visiblePreviewCount()),
  );
  protected readonly visiblePreviewDuos = computed(() =>
    this.previewDuos().slice(0, this.visiblePreviewCount()),
  );
  protected readonly participantPreviewColumns = computed(() =>
    groupChallengeCardPreviewItems(this.visiblePreviewParticipants(), this.previewColumnCount()),
  );
  protected readonly duoPreviewColumns = computed(() =>
    groupChallengeCardPreviewItems(this.visiblePreviewDuos(), this.previewColumnCount()),
  );
  protected readonly hasPreview = computed(
    () => this.previewParticipants().length > 0 || this.previewDuos().length > 0,
  );
  protected readonly remainingCount = computed(() =>
    Math.max(0, this.entryCount() - this.visiblePreviewCount()),
  );
  protected readonly showLeaderboardPreview = computed(
    () => this.challenge().status !== 'NOT_STARTED',
  );

  constructor() {
    effect(() => {
      if (this.hidePreview()) {
        return;
      }

      this.challenge();
      this.previewParticipants();
      this.previewDuos();
      this.visiblePreviewCount();
      this.previewColumnCount();
      this.participantPreviewColumns();
      this.duoPreviewColumns();
      queueMicrotask(() => this.updatePreviewFade());
    });
  }

  ngAfterViewInit(): void {
    if (this.hidePreview()) {
      return;
    }

    const viewportElement = this.previewViewport()?.nativeElement;
    const trackElement = this.previewTrack()?.nativeElement;
    if (!viewportElement || !trackElement) {
      this.updatePreviewFade();
      return;
    }

    if (typeof ResizeObserver === 'undefined') {
      this.updatePreviewFade();
      return;
    }

    this.resizeObserver = new ResizeObserver(() => {
      this.previewViewportWidth.set(viewportElement.clientWidth);
      this.updatePreviewFade();
    });
    this.resizeObserver.observe(viewportElement);
    this.resizeObserver.observe(trackElement);
    this.previewViewportWidth.set(viewportElement.clientWidth);
    this.updatePreviewFade();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  private updatePreviewFade(): void {
    const viewportElement = this.previewViewport()?.nativeElement;
    const trackElement = this.previewTrack()?.nativeElement;
    if (!viewportElement || !trackElement) {
      this.showPreviewFade.set(false);
      return;
    }

    this.showPreviewFade.set(trackElement.scrollWidth > viewportElement.clientWidth + 1);
  }

  protected readonly challengeTypeBadgeKind = challengeTypeBadgeKind;
  protected readonly challengeStatusBadgeKind = challengeStatusBadgeKind;
  protected readonly challengeRegionBadgeKind = challengeRegionBadgeKind;

  entryLimit(): number {
    return this.challenge().type === 'DUOQ' ? 8 : 16;
  }

  entryCountCompact(): string {
    return `${this.entryCount()}/${this.entryLimit()}`;
  }

  dateRangeCompact(): string {
    const locale = this.i18n.locale();
    const start = formatChallengeDateCompact(this.challenge().startAt, locale);
    const maxGames = this.challenge().maxGames;
    if (maxGames) {
      return `${start} → ${this.i18n.t('challenge.maxGamesCompact', { count: maxGames })}`;
    }
    const end = this.challenge().endAt
      ? formatChallengeDateCompact(this.challenge().endAt, locale)
      : null;
    return end ? `${start} → ${end}` : start;
  }

  metaCompactAriaLabel(): string {
    const parts = [this.dateRangeCompact(), this.entryCountCompact()].filter(Boolean);
    return parts.join(', ');
  }

  statusLabel(status: ChallengeSummary['status']): string {
    if (status === 'NOT_STARTED') {
      return this.i18n.t('challenge.statusNotStarted');
    }
    if (status === 'FINISHED') {
      return this.i18n.t('challenge.statusFinished');
    }
    return this.i18n.t('challenge.statusActive');
  }

  typeLabel(type: ChallengeSummary['type']): string {
    return type === 'SOLOQ' ? this.i18n.t('challenge.typeSoloq') : this.i18n.t('challenge.typeDuoq');
  }

  regionLabel(region: ChallengeSummary['region']): string {
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

  entryCountLabel(): string {
    const count = this.entryCount();
    if (this.challenge().type === 'DUOQ') {
      return this.i18n.t('challenge.duoCount', { count });
    }
    return this.i18n.t('challenge.entryCount', { count });
  }

  previewTitle(): string {
    return this.showLeaderboardPreview()
      ? this.i18n.t('challenge.previewLeaderboard')
      : this.i18n.t('challenge.previewRegistered');
  }

  rankLabel(participant: ParticipantPreview): string | null {
    if (!participant.currentTier) {
      return null;
    }

    const locale = this.i18n.locale();
    if (this.challenge().status === 'FINISHED') {
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
    return value > 0 ? 'challenge-card__lp--positive' : 'challenge-card__lp--negative';
  }

  previewItemClass(position: number, eligible = true): string {
    if (!eligible || !this.showLeaderboardPreview()) {
      return '';
    }
    if (position === 1) {
      return 'challenge-card__preview-item--gold';
    }
    if (position === 2) {
      return 'challenge-card__preview-item--silver';
    }
    if (position === 3) {
      return 'challenge-card__preview-item--bronze';
    }
    return '';
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
    return `challenge-card__winrate--${winRateToneModifier(winRate, wins, losses)}`;
  }
}
