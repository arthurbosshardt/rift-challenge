import { Component, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';

@Component({
  selector: 'app-player-identity',
  imports: [PlayerAvatarComponent, TranslatePipe],
  templateUrl: './player-identity.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './player-identity.component.scss',
})
export class PlayerIdentityComponent {
  private readonly i18n = inject(I18nService);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  readonly gameName = input.required<string>();
  readonly tagLine = input.required<string>();
  readonly riotId = input.required<string>();
  readonly profileIconId = input<number | null>(null);
  readonly tier = input<string | null>(null);
  readonly rankLabel = input<string | null>(null);
  readonly compact = input(false);

  protected readonly copiedField = signal<'name' | 'riotId' | null>(null);

  protected copyField(field: 'name' | 'riotId', value: string, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    void this.performCopy(field, value);
  }

  protected copyNameAria(): string {
    return this.i18n.t('player.copyRiotIdAria', { riotId: this.riotId() });
  }

  protected copyRiotIdAria(): string {
    return this.i18n.t('player.copyRiotIdAria', { riotId: this.riotId() });
  }

  private async performCopy(field: 'name' | 'riotId', value: string): Promise<void> {
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
