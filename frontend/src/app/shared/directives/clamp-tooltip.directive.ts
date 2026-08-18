import {
  Directive,
  ElementRef,
  HostListener,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';

const MOBILE_QUERY = '(max-width: 899px)';
const VIEWPORT_MARGIN = 12;
const GAP = 8;

@Directive({
  selector: '[appClampTooltip]',
})
export class ClampTooltipDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private tooltip: HTMLElement | null = null;
  private readonly mediaQuery = window.matchMedia(MOBILE_QUERY);
  private rafId: number | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly onMediaChange = (): void => {
    this.resetPosition();
  };

  ngOnInit(): void {
    this.tooltip = this.host.nativeElement.querySelector('[role="tooltip"]');
    this.mediaQuery.addEventListener('change', this.onMediaChange);
  }

  ngOnDestroy(): void {
    this.mediaQuery.removeEventListener('change', this.onMediaChange);
    this.cancelFrame();
    this.clearHideTimer();
    this.resetPosition();
  }

  @HostListener('mouseenter')
  @HostListener('focusin')
  scheduleClamp(): void {
    this.queueClamp();
  }

  @HostListener('touchend')
  onTouchEnd(): void {
    this.queueClamp(50);
  }

  @HostListener('click')
  onClick(): void {
    if (this.mediaQuery.matches) {
      this.queueClamp(0);
      this.queueClamp(120);
    }
  }

  @HostListener('mouseleave')
  @HostListener('focusout')
  onHide(): void {
    this.cancelFrame();
    this.clearHideTimer();
    this.hideTimer = setTimeout(() => {
      if (this.tooltip && this.isVisible(this.tooltip)) {
        return;
      }
      this.resetPosition();
    }, 150);
  }

  private queueClamp(delayMs = 0): void {
    if (!this.tooltip || !this.mediaQuery.matches) {
      return;
    }

    this.cancelFrame();
    this.clearHideTimer();

    const run = (): void => {
      this.rafId = requestAnimationFrame(() => {
        this.rafId = requestAnimationFrame(() => this.clampPosition());
      });
    };

    if (delayMs > 0) {
      this.hideTimer = setTimeout(run, delayMs);
      return;
    }

    run();
  }

  private clampPosition(): void {
    const tooltip = this.tooltip;
    if (!tooltip || !this.isVisible(tooltip)) {
      return;
    }

    const hostRect = this.host.nativeElement.getBoundingClientRect();
    tooltip.classList.add('tooltip--viewport-clamped');
    tooltip.style.position = 'fixed';
    tooltip.style.right = 'auto';
    tooltip.style.bottom = 'auto';
    tooltip.style.transform = 'none';
    tooltip.style.zIndex = getComputedStyle(document.documentElement).getPropertyValue('--z-tooltip').trim() || '2400';
    tooltip.style.maxWidth = `${Math.max(0, window.innerWidth - VIEWPORT_MARGIN * 2)}px`;

    tooltip.style.top = `${hostRect.bottom + GAP}px`;
    tooltip.style.left = `${hostRect.left}px`;

    let rect = tooltip.getBoundingClientRect();

    if (rect.bottom > window.innerHeight - VIEWPORT_MARGIN) {
      const aboveTop = hostRect.top - GAP - rect.height;
      if (aboveTop >= VIEWPORT_MARGIN) {
        tooltip.style.top = `${aboveTop}px`;
        rect = tooltip.getBoundingClientRect();
      }
    }

    if (rect.top < VIEWPORT_MARGIN) {
      tooltip.style.top = `${VIEWPORT_MARGIN}px`;
      rect = tooltip.getBoundingClientRect();
    }

    if (rect.bottom > window.innerHeight - VIEWPORT_MARGIN) {
      tooltip.style.top = `${Math.max(VIEWPORT_MARGIN, window.innerHeight - VIEWPORT_MARGIN - rect.height)}px`;
      rect = tooltip.getBoundingClientRect();
    }

    if (rect.right > window.innerWidth - VIEWPORT_MARGIN) {
      tooltip.style.left = `${window.innerWidth - VIEWPORT_MARGIN - rect.width}px`;
      rect = tooltip.getBoundingClientRect();
    }

    if (rect.left < VIEWPORT_MARGIN) {
      tooltip.style.left = `${VIEWPORT_MARGIN}px`;
    }
  }

  private isVisible(tooltip: HTMLElement): boolean {
    const style = getComputedStyle(tooltip);
    return style.visibility !== 'hidden' && parseFloat(style.opacity) > 0.01;
  }

  private resetPosition(): void {
    const tooltip = this.tooltip;
    if (!tooltip) {
      return;
    }

    tooltip.classList.remove('tooltip--viewport-clamped');
    tooltip.style.removeProperty('position');
    tooltip.style.removeProperty('top');
    tooltip.style.removeProperty('left');
    tooltip.style.removeProperty('right');
    tooltip.style.removeProperty('bottom');
    tooltip.style.removeProperty('transform');
    tooltip.style.removeProperty('z-index');
    tooltip.style.removeProperty('max-width');
  }

  private cancelFrame(): void {
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  private clearHideTimer(): void {
    if (this.hideTimer != null) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
  }
}
