import {
  Directive,
  ElementRef,
  HostListener,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';

import { MOBILE_MEDIA_QUERY } from '../../core/layout/breakpoints';

const VIEWPORT_MARGIN = 12;

@Directive({
  selector: '[appClampTooltip]',
})
export class ClampTooltipDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private tooltip: HTMLElement | null = null;
  private readonly mediaQuery = window.matchMedia(MOBILE_MEDIA_QUERY);
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
    if (!this.tooltip) {
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

    // Measure the tooltip exactly where CSS naturally places it (undo any
    // previous clamp first, so we don't measure our own past correction).
    this.resetPosition();
    const naturalMaxWidth = Math.max(0, window.innerWidth - VIEWPORT_MARGIN * 2);
    const natural = tooltip.getBoundingClientRect();

    const overflowsLeft = natural.left < VIEWPORT_MARGIN;
    const overflowsRight = natural.right > window.innerWidth - VIEWPORT_MARGIN;
    const overflowsTop = natural.top < VIEWPORT_MARGIN;
    const overflowsBottom = natural.bottom > window.innerHeight - VIEWPORT_MARGIN;

    if (!overflowsLeft && !overflowsRight && !overflowsTop && !overflowsBottom) {
      return;
    }

    tooltip.classList.add('tooltip--viewport-clamped');
    tooltip.style.position = 'fixed';
    tooltip.style.right = 'auto';
    tooltip.style.bottom = 'auto';
    tooltip.style.transform = 'none';
    tooltip.style.zIndex = getComputedStyle(document.documentElement).getPropertyValue('--z-tooltip').trim() || '2400';
    tooltip.style.maxWidth = `${naturalMaxWidth}px`;

    let left = natural.left;
    if (left < VIEWPORT_MARGIN) {
      left = VIEWPORT_MARGIN;
    } else if (left + natural.width > window.innerWidth - VIEWPORT_MARGIN) {
      left = Math.max(VIEWPORT_MARGIN, window.innerWidth - VIEWPORT_MARGIN - natural.width);
    }

    let top = natural.top;
    if (top < VIEWPORT_MARGIN) {
      top = VIEWPORT_MARGIN;
    } else if (top + natural.height > window.innerHeight - VIEWPORT_MARGIN) {
      top = Math.max(VIEWPORT_MARGIN, window.innerHeight - VIEWPORT_MARGIN - natural.height);
    }

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
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
