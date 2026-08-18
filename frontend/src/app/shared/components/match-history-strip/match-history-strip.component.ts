import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { DuoMatchHistory, ParticipantMatchHistory } from '../../../core/models/challenge.models';
import { I18nService } from '../../../core/i18n/i18n.service';
import { apiUrl } from '../../../core/utils/api-url';
import {
  formatMatchHistoryDayLabel,
  groupMatchHistoryByLocalDay,
} from '../../../core/utils/match-history-groups';

@Component({
  selector: 'app-match-history-strip',
  imports: [],
  templateUrl: './match-history-strip.component.html',
  styleUrl: './match-history-strip.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class MatchHistoryStripComponent implements AfterViewInit, OnDestroy {
  readonly soloEntries = input<ParticipantMatchHistory[]>([]);
  readonly duoEntries = input<DuoMatchHistory[]>([]);
  readonly duoMode = input(false);

  private readonly i18n = inject(I18nService);
  private readonly viewport = viewChild<ElementRef<HTMLElement>>('viewport');
  private readonly track = viewChild<ElementRef<HTMLElement>>('track');
  private resizeObserver: ResizeObserver | null = null;

  protected readonly showRightFade = signal(false);

  protected readonly soloGroups = computed(() =>
    groupMatchHistoryByLocalDay(this.soloEntries()),
  );

  protected readonly duoGroups = computed(() =>
    groupMatchHistoryByLocalDay(this.duoEntries()),
  );

  protected readonly hasHistory = computed(() =>
    this.duoMode() ? this.duoEntries().length > 0 : this.soloEntries().length > 0,
  );

  constructor() {
    effect(() => {
      this.soloEntries();
      this.duoEntries();
      this.duoMode();
      this.soloGroups();
      this.duoGroups();
      queueMicrotask(() => this.updateRightFade());
    });
  }

  ngAfterViewInit(): void {
    const viewportElement = this.viewport()?.nativeElement;
    const trackElement = this.track()?.nativeElement;
    if (!viewportElement || !trackElement) {
      this.updateRightFade();
      return;
    }

    viewportElement.addEventListener('scroll', this.onViewportScroll, { passive: true });

    if (typeof ResizeObserver === 'undefined') {
      this.updateRightFade();
      return;
    }

    this.resizeObserver = new ResizeObserver(() => {
      this.updateRightFade();
    });
    this.resizeObserver.observe(viewportElement);
    this.resizeObserver.observe(trackElement);
    this.updateRightFade();
  }

  ngOnDestroy(): void {
    this.viewport()?.nativeElement.removeEventListener('scroll', this.onViewportScroll);
    this.resizeObserver?.disconnect();
  }

  private readonly onViewportScroll = (): void => {
    this.updateRightFade();
  };

  private updateRightFade(): void {
    const viewportElement = this.viewport()?.nativeElement;
    const trackElement = this.track()?.nativeElement;
    if (!viewportElement || !trackElement) {
      this.showRightFade.set(false);
      return;
    }

    const overflows = trackElement.scrollWidth > viewportElement.clientWidth + 1;
    const scrolledToEnd =
      viewportElement.scrollLeft + viewportElement.clientWidth >= trackElement.scrollWidth - 1;
    this.showRightFade.set(overflows && !scrolledToEnd);
  }

  protected dayLabel(dayKey: string): string {
    this.i18n.locale();
    return formatMatchHistoryDayLabel(dayKey, this.i18n.locale());
  }

  protected matchIconSrc(
    championId: number | null | undefined,
    championIconUrl: string | null | undefined,
  ): string | null {
    if (championIconUrl) {
      return apiUrl(championIconUrl);
    }
    if (championId != null && championId > 0) {
      return apiUrl(`/api/champion-icons/${championId}.png`);
    }
    return null;
  }
}
