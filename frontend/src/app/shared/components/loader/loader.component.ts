import { Component, input } from '@angular/core';

@Component({
  selector: 'app-loader',
  template: `
    <div class="loader" [class.loader--compact]="compact()" role="status" [attr.aria-label]="label() || 'Loading'">
      <span class="loader__spinner" aria-hidden="true"></span>
      @if (label()) {
        <span class="loader__label">{{ label() }}</span>
      }
    </div>
  `,
  styles: `
    .loader {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--text-muted);
    }

    .loader__spinner {
      width: 1.1rem;
      height: 1.1rem;
      border: 2px solid rgba(200, 170, 110, 0.2);
      border-top-color: var(--gold);
      border-radius: 50%;
      animation: loader-spin 0.7s linear infinite;
    }

    .loader--compact .loader__spinner {
      width: 0.85rem;
      height: 0.85rem;
    }

    .loader__label {
      font-size: 0.95rem;
    }

    @keyframes loader-spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class LoaderComponent {
  readonly label = input<string>('');
  readonly compact = input(false);
}
