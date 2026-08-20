import {
  Directive,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { MOBILE_MEDIA_QUERY } from '../../core/layout/breakpoints';

const MIN_FONT_SIZE_PX = 10;
// Linear font-scaling isn't perfectly exact across fonts/browsers (hinting,
// kerning); shrink a hair further than the raw ratio so a rounding sliver
// doesn't tip the text back into ellipsis-truncated territory.
const SAFETY_MARGIN = 0.97;

/**
 * Shrinks the host's font-size, on mobile only, so its text spans exactly the
 * width its flex box already allocates it (no leftover gap, no ellipsis
 * truncation) — CSS has no way to measure rendered text and size itself to
 * fit, so this has to happen in JS. Never grows past the CSS-defined max;
 * only shrinks.
 */
@Directive({
  selector: '[appFitTitleWidth]',
})
export class FitTitleWidthDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly mediaQuery = window.matchMedia(MOBILE_MEDIA_QUERY);
  private resizeObserver: ResizeObserver | null = null;
  private mutationObserver: MutationObserver | null = null;

  private readonly onMediaChange = (): void => this.fit();

  ngOnInit(): void {
    this.mediaQuery.addEventListener('change', this.onMediaChange);

    const element = this.host.nativeElement;

    // ResizeObserver fires once right after observe() (covering the initial
    // fit) and again on every subsequent box-size change (covering window
    // resizes and sibling elements changing width) — one mechanism handles
    // both cases, no manual kickoff needed.
    this.resizeObserver = new ResizeObserver(() => this.fit());
    this.resizeObserver.observe(element);

    // Text content can change without the box resizing (e.g. renaming a
    // challenge while viewing it), which ResizeObserver won't catch.
    this.mutationObserver = new MutationObserver(() => this.fit());
    this.mutationObserver.observe(element, { characterData: true, childList: true, subtree: true });

    // The brand font can still be loading when the first fit runs, which
    // under-measures width (fallback font is narrower) and skips the
    // shrink; re-fit once the real font is in so it doesn't silently overflow.
    document.fonts?.ready?.then(() => this.fit());
  }

  ngOnDestroy(): void {
    this.mediaQuery.removeEventListener('change', this.onMediaChange);
    this.resizeObserver?.disconnect();
    this.mutationObserver?.disconnect();
  }

  private fit(): void {
    const element = this.host.nativeElement;

    if (!this.mediaQuery.matches) {
      element.style.removeProperty('font-size');
      return;
    }

    // Measure at the CSS-defined (max) font-size, undoing any previous
    // shrink first, so the natural-width reading isn't self-referential.
    element.style.removeProperty('font-size');

    const availableWidth = element.clientWidth;
    const naturalWidth = element.scrollWidth;
    if (availableWidth <= 0 || naturalWidth <= availableWidth) {
      return;
    }

    const maxFontSizePx = parseFloat(getComputedStyle(element).fontSize);
    const fittedFontSizePx = Math.max(
      MIN_FONT_SIZE_PX,
      maxFontSizePx * (availableWidth / naturalWidth) * SAFETY_MARGIN,
    );
    element.style.fontSize = `${fittedFontSizePx}px`;
  }
}
