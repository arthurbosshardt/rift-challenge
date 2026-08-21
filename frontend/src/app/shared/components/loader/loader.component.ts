import { Component, input, ChangeDetectionStrategy, inject } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-loader',
  host: {
    '[class.loader-host--compact]': 'compact()',
    '[class.loader-host--centered]': 'centered() && !compact()',
  },
  template: `
    <div class="loader" [class.loader--compact]="compact()" role="status" [attr.aria-label]="label() || i18n.t('common.loading')">
      <span class="loader__spinner" aria-hidden="true"></span>
      @if (label()) {
        <span class="loader__label">{{ label() }}</span>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: block;
    }

    :host(.loader-host--centered) {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      min-height: min(45vh, 22rem);
      flex: 1;
    }

    :host(.loader-host--compact) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: auto;
      min-height: auto;
      flex: none;
    }

    .loader {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--text-muted);
    }

    .loader__spinner {
      width: 1.1rem;
      height: 1.1rem;
      border: 2px solid var(--gold-a20);
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
  protected readonly i18n = inject(I18nService);
  readonly label = input<string>('');
  readonly compact = input(false);
  readonly centered = input(true);
}
