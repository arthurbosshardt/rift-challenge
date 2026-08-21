import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CreateChallengeModalService } from '../../core/services/create-challenge-modal.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { LeaderboardComponent } from '../../shared/components/leaderboard/leaderboard.component';

const ABOUT_BUBBLE_STORAGE_KEY = 'riftchallenge.home.aboutDismissed';

@Component({
  selector: 'app-landing-page',
  imports: [PageShellComponent, RouterLink, TranslatePipe, LeaderboardComponent],
  templateUrl: './landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  protected readonly auth = inject(AuthService);
  protected readonly createChallengeModal = inject(CreateChallengeModalService);

  protected readonly aboutBubbleVisible = signal(this.readAboutBubbleVisible());

  protected dismissAboutBubble(): void {
    this.aboutBubbleVisible.set(false);
    try {
      localStorage.setItem(ABOUT_BUBBLE_STORAGE_KEY, '1');
    } catch {
      /* ignore quota / private mode */
    }
  }

  private readAboutBubbleVisible(): boolean {
    try {
      return localStorage.getItem(ABOUT_BUBBLE_STORAGE_KEY) !== '1';
    } catch {
      return true;
    }
  }
}
