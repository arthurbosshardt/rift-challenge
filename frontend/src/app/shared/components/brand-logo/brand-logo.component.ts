import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { brandLogoUrl } from '../../../core/brand/brand-logo';

/** The frame 'xl' reserves in normal layout — it renders larger than this
 * but is absolutely centered inside a frame pinned to this size, so it
 * overflows visually above/below the header instead of growing it. */
const HEADER_ROW_BASELINE = 36;

@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <span class="brand-logo-frame" [class.brand-logo-frame--pinned]="size() === 'xl'">
      <img
        class="brand-logo"
        [class.brand-logo--sm]="size() === 'sm'"
        [class.brand-logo--overflow]="size() === 'xl'"
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
      width: ${HEADER_ROW_BASELINE}px;
      height: ${HEADER_ROW_BASELINE}px;
    }

    .brand-logo {
      border-radius: var(--radius-sm, 8px);
      display: block;
    }

    .brand-logo--overflow {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
    }
  `,
})
export class BrandLogoComponent {
  readonly size = input<'md' | 'sm' | 'lg' | 'xl'>('md');
  protected readonly logoSrc = brandLogoUrl();

  protected dimension(): number {
    switch (this.size()) {
      case 'sm':
        return 28;
      case 'lg':
        return 224;
      case 'xl':
        return 144;
      default:
        return HEADER_ROW_BASELINE;
    }
  }
}
