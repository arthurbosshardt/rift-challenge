import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { brandLogoUrl } from '../../../core/brand/brand-logo';

/** 'xl' and 'modal-lg' render larger than this but reserve only this much
 * height in normal layout (full width is still reserved, so nothing
 * overlaps horizontally) — the image overflows above/below instead of
 * growing its container. */
const PINNED_HEIGHT = 36;

export type BrandLogoSize = 'md' | 'sm' | 'lg' | 'xl' | 'modal-lg';

@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <span
      class="brand-logo-frame"
      [class.brand-logo-frame--pinned]="isPinned()"
      [style.width.px]="isPinned() ? dimension() : null"
    >
      @if (size() === 'xl') {
        <span class="brand-logo-mask" [style.width.px]="dimension()" [style.height.px]="dimension()"></span>
      }
      <img
        class="brand-logo"
        [class.brand-logo--sm]="size() === 'sm'"
        [class.brand-logo--pinned]="isPinned()"
        [src]="logoSrc"
        [width]="dimension()"
        [height]="dimension()"
        alt=""
        aria-hidden="true"
        decoding="async"
      />
    </span>
  `,
  styles: `
    :host {
      display: inline-flex;
      line-height: 0;
    }

    .brand-logo-frame {
      display: inline-block;
      position: relative;
      line-height: 0;
    }

    .brand-logo-frame--pinned {
      height: ${PINNED_HEIGHT}px;
    }

    .brand-logo {
      border-radius: var(--radius-sm, 8px);
      display: block;
    }

    .brand-logo--pinned {
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
    }

    /* Sits behind the (possibly transparent) logo artwork so the header's
       border-bottom doesn't show through wherever the oversized logo
       overflows past it — only the segment the logo actually covers. */
    .brand-logo-mask {
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      background: var(--bg);
      border-radius: var(--radius-sm, 8px);
    }
  `,
})
export class BrandLogoComponent {
  readonly size = input<BrandLogoSize>('md');
  protected readonly logoSrc = brandLogoUrl();

  protected isPinned(): boolean {
    const size = this.size();
    return size === 'xl' || size === 'modal-lg';
  }

  protected dimension(): number {
    switch (this.size()) {
      case 'sm':
        return 28;
      case 'lg':
        return 224;
      case 'xl':
        return 144;
      case 'modal-lg':
        return 72;
      default:
        return PINNED_HEIGHT;
    }
  }
}
