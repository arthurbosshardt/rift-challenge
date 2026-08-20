import {
  Directive,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { MOBILE_MEDIA_QUERY } from '../../core/layout/breakpoints';

const HIDDEN_CLASS = 'truncated-hidden';

/**
 * Hides its host element entirely (rather than letting it show an ellipsis)
 * when the host's text content overflows its box, on mobile viewports only.
 * Desktop keeps the normal `text-overflow: ellipsis` truncation.
 */
@Directive({
  selector: '[appHideIfTruncated]',
})
export class HideIfTruncatedDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly mediaQuery = window.matchMedia(MOBILE_MEDIA_QUERY);
  private resizeObserver: ResizeObserver | null = null;
  private mutationObserver: MutationObserver | null = null;
  private rafId: number | null = null;

  private readonly onMediaChange = (): void => this.scheduleCheck();

  ngOnInit(): void {
    this.mediaQuery.addEventListener('change', this.onMediaChange);

    const element = this.host.nativeElement;

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.scheduleCheck());
      this.resizeObserver.observe(element);
    }

    if (typeof MutationObserver !== 'undefined') {
      this.mutationObserver = new MutationObserver(() => this.scheduleCheck());
      this.mutationObserver.observe(element, { characterData: true, childList: true, subtree: true });
    }

    this.scheduleCheck();
  }

  ngOnDestroy(): void {
    this.mediaQuery.removeEventListener('change', this.onMediaChange);
    this.resizeObserver?.disconnect();
    this.mutationObserver?.disconnect();
    this.cancelFrame();
  }

  private scheduleCheck(): void {
    this.cancelFrame();
    this.rafId = requestAnimationFrame(() => this.check());
  }

  private check(): void {
    const element = this.host.nativeElement;
    element.classList.remove(HIDDEN_CLASS);
    const isTruncated = this.mediaQuery.matches && element.scrollWidth > element.clientWidth + 1;
    element.classList.toggle(HIDDEN_CLASS, isTruncated);
  }

  private cancelFrame(): void {
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }
}
