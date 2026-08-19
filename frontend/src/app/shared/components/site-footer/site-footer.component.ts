import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { LAST_UPDATED_AT, formatLastUpdatedDate } from '../../../core/version';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';

@Component({
  selector: 'app-site-footer',
  imports: [TranslatePipe],
  templateUrl: './site-footer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './site-footer.component.scss',
})
export class SiteFooterComponent {
  private readonly i18n = inject(I18nService);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly creatorEmail = 'tanor.pro@gmail.com';
  protected readonly creatorDiscord = '_tanor';
  protected readonly copiedField = signal<'discord' | 'email' | null>(null);
  protected readonly lastUpdatedLabel = computed(() => {
    const date = formatLastUpdatedDate(LAST_UPDATED_AT, this.i18n.locale());
    return this.i18n.t('footer.lastUpdated', { date });
  });

  protected copyDiscordAria(): string {
    return this.i18n.t('footer.copyDiscordAria', { username: this.creatorDiscord });
  }

  protected copyEmailAria(): string {
    return this.i18n.t('footer.copyEmailAria', { email: this.creatorEmail });
  }

  protected copyField(field: 'discord' | 'email', value: string): void {
    void this.performCopy(field, value);
  }

  private async performCopy(field: 'discord' | 'email', value: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      this.copiedField.set(field);
      if (this.copyResetTimer) {
        clearTimeout(this.copyResetTimer);
      }
      this.copyResetTimer = setTimeout(() => this.copiedField.set(null), 1500);
    } catch {
      /* clipboard unavailable */
    }
  }
}
