import { Component, effect, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { profileIconInitial, profileIconUrls } from '../../../core/utils/profile-icon';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-player-avatar',
  imports: [RankEmblemComponent],
  templateUrl: './player-avatar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './player-avatar.component.scss',
})
export class PlayerAvatarComponent {
  readonly profileIconId = input<number | null>(null);
  readonly gameName = input<string>('');
  readonly tier = input<string | null>(null);
  readonly size = input<'xs' | 'sm' | 'md'>('md');
  private readonly i18n = inject(I18nService);

  protected readonly iconFailed = signal(false);
  private readonly iconUrlIndex = signal(0);

  constructor() {
    effect(() => {
      this.profileIconId();
      this.iconFailed.set(false);
      this.iconUrlIndex.set(0);
    });
  }

  protected iconUrl(): string | null {
    if (this.iconFailed()) {
      return null;
    }
    const urls = profileIconUrls(this.profileIconId());
    const index = this.iconUrlIndex();
    return urls[index] ?? null;
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
    const nextIndex = this.iconUrlIndex() + 1;
    if (nextIndex < profileIconUrls(this.profileIconId()).length) {
      this.iconUrlIndex.set(nextIndex);
      return;
    }
    this.iconFailed.set(true);
  }
}
