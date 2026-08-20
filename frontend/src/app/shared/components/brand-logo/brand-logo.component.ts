import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { brandLogoUrl } from '../../../core/brand/brand-logo';

@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <img
      class="brand-logo"
      [class.brand-logo--sm]="size() === 'sm'"
      [src]="logoSrc"
      [width]="dimension()"
      [height]="dimension()"
      alt=""
      aria-hidden="true"
      decoding="async"
    />
  `,
  styles: `
    :host {
      display: inline-flex;
      line-height: 0;
    }

    .brand-logo {
      border-radius: var(--radius-sm, 8px);
      display: block;
    }
  `,
})
export class BrandLogoComponent {
  readonly size = input<'md' | 'sm' | 'lg'>('md');
  protected readonly logoSrc = brandLogoUrl();

  protected dimension(): number {
    switch (this.size()) {
      case 'sm':
        return 28;
      case 'lg':
        return 112;
      default:
        return 72;
    }
  }
}
