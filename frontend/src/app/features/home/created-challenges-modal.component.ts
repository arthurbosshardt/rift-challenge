import {
  Component,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { CreatedChallengesModalService } from '../../core/services/created-challenges-modal.service';
import { ChallengeSummary } from '../../core/models/challenge.models';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-created-challenges-modal',
  imports: [ChallengeCardComponent, ChallengeListSkeletonComponent, TranslatePipe, NavIconComponent],
  templateUrl: './created-challenges-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './created-challenges-modal.component.scss',
})
export class CreatedChallengesModalComponent {
  protected readonly createdChallengesModal = inject(CreatedChallengesModalService);
  private readonly challengeApi = inject(ChallengeApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  protected readonly challenges = signal<ChallengeSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.createdChallengesModal.isOpen()) {
        document.body.style.overflow = 'hidden';
        void this.loadChallenges();
        return;
      }

      document.body.style.overflow = '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.createdChallengesModal.isOpen() && !this.loading()) {
      this.close();
    }
  }

  protected close(): void {
    this.createdChallengesModal.close();
  }

  private async loadChallenges(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);

    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      this.error.set(this.i18n.t('home.sessionExpired'));
      this.loading.set(false);
      return;
    }

    this.challengeApi.listOwnedChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.error.set(this.i18n.t('home.sessionExpired'));
        } else {
          this.error.set(this.i18n.t('home.loadMineError'));
        }
        this.loading.set(false);
      },
    });
  }
}
