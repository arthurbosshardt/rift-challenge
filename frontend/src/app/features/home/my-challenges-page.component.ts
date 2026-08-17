import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { ChallengeSummary } from '../../core/models/challenge.models';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-my-challenges-page',
  imports: [PageShellComponent, ChallengeCardComponent, ChallengeListSkeletonComponent, TranslatePipe],
  templateUrl: './my-challenges-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './my-challenges-page.component.scss',
})
export class MyChallengesPageComponent implements OnInit {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly i18n = inject(I18nService);

  protected readonly challenges = signal<ChallengeSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.loadPage();
  }

  private async loadPage(): Promise<void> {
    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      this.error.set(this.i18n.t('home.sessionExpired'));
      this.loading.set(false);
      return;
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    if (!this.auth.linkedAccount()) {
      this.loading.set(false);
      return;
    }

    void this.loadChallenges();
  }

  private async loadChallenges(): Promise<void> {
    this.loading.set(true);
    this.challengeApi.listParticipatingChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.error.set(this.i18n.t('home.sessionExpired'));
        } else {
          this.error.set(this.i18n.t('home.loadParticipatingError'));
        }
        this.loading.set(false);
      },
    });
  }
}
