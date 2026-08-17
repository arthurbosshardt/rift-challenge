import { Component, effect, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { profileIconInitial, profileIconUrl } from '../../../core/utils/profile-icon';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-player-avatar',
  imports: [RankEmblemComponent],
  templateUrl: './player-avatar.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './player-avatar.component.scss',
})
export class PlayerAvatarComponent {
  readonly profileIconId = input<number | null>(null);
  readonly gameName = input<string>('');
  readonly tier = input<string | null>(null);
  readonly size = input<'xs' | 'sm' | 'md'>('md');
  private readonly i18n = inject(I18nService);

  protected readonly iconFailed = signal(false);

  constructor() {
    effect(() => {
      this.profileIconId();
      this.iconFailed.set(false);
    });
  }

  protected iconUrl(): string | null {
    if (this.iconFailed()) {
      return null;
    }
    return profileIconUrl(this.profileIconId());
  }

  protected initial(): string {
    return profileIconInitial(this.gameName());
  }

  protected iconAlt(): string {
    return this.i18n.t('avatar.iconAlt', {
      name: this.gameName() || this.i18n.t('avatar.player'),
    });
  }

  protected onIconError(): void {
    this.iconFailed.set(true);
  }
}
