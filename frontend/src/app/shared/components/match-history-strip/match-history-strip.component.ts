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
  output,
  signal,
  viewChild,
} from '@angular/core';
import { DuoMatchHistory, ParticipantMatchHistory } from '../../../core/models/challenge.models';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { apiUrl } from '../../../core/utils/api-url';
import {
  formatMatchHistoryDayLabel,
  groupMatchHistoryByLocalDay,
} from '../../../core/utils/match-history-groups';

@Component({
  selector: 'app-match-history-strip',
  imports: [TranslatePipe],
  templateUrl: './match-history-strip.component.html',
  styleUrl: './match-history-strip.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchHistoryStripComponent implements AfterViewInit, OnDestroy {
  readonly soloEntries = input<ParticipantMatchHistory[]>([]);
  readonly duoEntries = input<DuoMatchHistory[]>([]);
  readonly duoMode = input(false);
  readonly entryClick = output<string>();

  private readonly i18n = inject(I18nService);
  private readonly viewport = viewChild<ElementRef<HTMLElement>>('viewport');
  private readonly track = viewChild<ElementRef<HTMLElement>>('track');
  private resizeObserver: ResizeObserver | null = null;

  protected readonly showRightFade = signal(false);
  protected readonly dragging = signal(false);
  private pointerDownActive = false;
  private activePointerId: number | null = null;
  private dragStartX = 0;
  private dragStartScrollLeft = 0;
  private dragMoved = false;
  private static readonly DRAG_THRESHOLD_PX = 4;

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

  protected onPointerDown(event: PointerEvent): void {
    if (event.pointerType !== 'mouse') {
      return;
    }
    const viewportElement = this.viewport()?.nativeElement;
    if (!viewportElement) {
      return;
    }
    this.pointerDownActive = true;
    this.activePointerId = event.pointerId;
    this.dragMoved = false;
    this.dragStartX = event.clientX;
    this.dragStartScrollLeft = viewportElement.scrollLeft;
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.pointerDownActive || event.pointerId !== this.activePointerId) {
      return;
    }
    const viewportElement = this.viewport()?.nativeElement;
    if (!viewportElement) {
      return;
    }
    const deltaX = event.clientX - this.dragStartX;
    if (!this.dragMoved && Math.abs(deltaX) > MatchHistoryStripComponent.DRAG_THRESHOLD_PX) {
      this.dragMoved = true;
      this.dragging.set(true);
      viewportElement.setPointerCapture(event.pointerId);
    }
    if (this.dragMoved) {
      event.preventDefault();
      viewportElement.scrollLeft = this.dragStartScrollLeft - deltaX;
    }
  }

  protected onPointerUp(event: PointerEvent): void {
    if (!this.pointerDownActive || event.pointerId !== this.activePointerId) {
      return;
    }
    this.pointerDownActive = false;
    this.activePointerId = null;
    if (this.dragMoved) {
      this.viewport()?.nativeElement.releasePointerCapture(event.pointerId);
    }
    this.dragging.set(false);
  }

  protected dayLabel(dayKey: string): string {
    this.i18n.locale();
    return formatMatchHistoryDayLabel(dayKey, this.i18n.locale());
  }

  protected onEntryClick(matchId: string): void {
    if (this.dragMoved) {
      return;
    }
    this.entryClick.emit(matchId);
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
