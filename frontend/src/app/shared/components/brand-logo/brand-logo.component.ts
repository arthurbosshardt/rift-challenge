import { Component, input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <svg
      class="brand-logo"
      [class.brand-logo--sm]="size() === 'sm'"
      [attr.width]="dimension()"
      [attr.height]="dimension()"
      viewBox="0 0 64 64"
      role="img"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="brand-logo-gold" x1="14" y1="10" x2="50" y2="54" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="#e8c882" />
          <stop offset="0.45" stop-color="#c5a059" />
          <stop offset="1" stop-color="#8a6f3a" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="10" fill="#12171d" />
      <path
        fill="url(#brand-logo-gold)"
        d="M14 42c0-2 1-4 3-5 2-8 6-14 12-18 1-6 4-11 9-14 5 2 8 6 10 11 1 3 1 6 0 9 5 3 8 8 9 14 1 4-1 8-4 10-3 2-7 2-10 0-4-3-7-8-8-13-5 2-9 6-11 11-2 4-5 6-9 6s-7-2-7-6z"
      />
      <path fill="#12171d" d="M33 30c-3 0-5 2-5 5s2 5 5 5 5-2 5-5-2-5-5-5z" />
      <path
        fill="url(#brand-logo-gold)"
        opacity="0.85"
        d="M16 38c2-6 6-10 11-12-1 4-1 8 1 12-3 1-5 3-6 6-2-1-4-2-6-6z"
      />
    </svg>
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
  readonly size = input<'md' | 'sm'>('md');

  protected dimension(): number {
    return this.size() === 'sm' ? 28 : 36;
  }
}
