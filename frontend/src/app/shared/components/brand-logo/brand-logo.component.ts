import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { brandLogoUrl } from '../../../core/brand/brand-logo';

/** 'xl' and 'modal-lg' render larger than this but reserve only this much
 * height in normal layout (full width is still reserved, so nothing
 * overlaps horizontally) — the image overflows above/below instead of
 * growing its container. */
const PINNED_HEIGHT = 36;

/** 'xl' shrinks to this on mobile/tablet so it doesn't run off the edge
 * of narrow viewports. */
const MOBILE_XL_SIZE = 96;

export type BrandLogoSize = 'md' | 'sm' | 'lg' | 'xl' | 'modal-lg';

@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="brand-logo-frame"
      [class.brand-logo-frame--pinned]="isPinned()"
      [class.brand-logo-frame--xl]="size() === 'xl'"
      [style.width.px]="isPinned() ? dimension() : null"
    >
      <img
        class="brand-logo"
        [class.brand-logo--sm]="size() === 'sm'"
        [class.brand-logo--pinned]="isPinned()"
        [class.brand-logo--xl]="size() === 'xl'"
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

    /* 'xl' overflows mostly downward instead of being centered — centering
       it would push it far enough above the header to run past the top of
       the viewport on short-scroll pages. */
    .brand-logo--xl {
      top: -20px;
      transform: none;
    }

    @media (max-width: 1023px) {
      .brand-logo-frame--xl {
        width: ${MOBILE_XL_SIZE}px !important;
      }

      .brand-logo--xl {
        width: ${MOBILE_XL_SIZE}px !important;
        height: ${MOBILE_XL_SIZE}px !important;
        top: -12px;
      }
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
