import { Component, effect, input, signal } from '@angular/core';
import { profileIconInitial, profileIconUrl } from '../../../core/utils/profile-icon';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';

@Component({
  selector: 'app-player-avatar',
  imports: [RankEmblemComponent],
  templateUrl: './player-avatar.component.html',
  styleUrl: './player-avatar.component.scss',
})
export class PlayerAvatarComponent {
  readonly profileIconId = input<number | null>(null);
  readonly gameName = input<string>('');
  readonly tier = input<string | null>(null);
  readonly size = input<'sm' | 'md'>('md');

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
    return `Icône de ${this.gameName() || 'joueur'}`;
  }

  protected onIconError(): void {
    this.iconFailed.set(true);
  }
}
