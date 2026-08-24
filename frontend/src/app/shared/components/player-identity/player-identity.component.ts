import { Component, inject, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { copyTextToClipboard } from '../../../core/utils/clipboard';
import { tierColor } from '../../../core/utils/rank-display';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { RankEmblemComponent } from '../rank-emblem/rank-emblem.component';
import { LeaderboardCategoryIconComponent } from '../leaderboard-category-icon/leaderboard-category-icon.component';

@Component({
  selector: 'app-player-identity',
  imports: [PlayerAvatarComponent, RankEmblemComponent, TranslatePipe, LeaderboardCategoryIconComponent],
  templateUrl: './player-identity.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './player-identity.component.scss',
})
export class PlayerIdentityComponent {
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private copyResetTimer: ReturnType<typeof setTimeout> | null = null;

  readonly gameName = input.required<string>();
  readonly tagLine = input.required<string>();
  readonly riotId = input.required<string>();
  readonly profileIconId = input<number | null>(null);
  readonly tier = input<string | null>(null);
  readonly rankLabel = input<string | null>(null);
  readonly compact = input(false);
  readonly linkToProfile = input(false);
  /** 'overlay' (default) draws the rank emblem over the avatar corner; 'end' gives it its own space after the text and hides it on mobile. */
  readonly rankBadgePosition = input<'overlay' | 'end'>('overlay');

  protected avatarTier(): string | null {
    return this.rankBadgePosition() === 'end' ? null : this.tier();
  }

  protected rankColor(): string {
    return tierColor(this.tier());
  }

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

  protected viewProfileAria(): string {
    return this.i18n.t('player.viewProfileAria', { riotId: this.riotId() });
  }

  protected goToProfile(event: Event): void {
    event.stopPropagation();
    void this.router.navigate(['/players', this.riotId()]);
  }

  private async performCopy(field: 'name' | 'riotId', value: string): Promise<void> {
    if (!(await copyTextToClipboard(value))) {
      return;
    }
    this.copiedField.set(field);
    if (this.copyResetTimer) {
      clearTimeout(this.copyResetTimer);
    }
    this.copyResetTimer = setTimeout(() => this.copiedField.set(null), 1500);
  }
}
