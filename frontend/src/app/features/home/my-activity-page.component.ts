import { Component, inject, OnDestroy, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { AccountRecentGames, ChallengeSummary, ParticipantMatchHistory } from '../../core/models/challenge.models';
import { formatRankLabel, tierColor } from '../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../core/utils/record-display';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeCardComponent } from '../../shared/components/challenge-card/challenge-card.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { PlayerIdentityComponent } from '../../shared/components/player-identity/player-identity.component';
import { MatchHistoryStripComponent } from '../../shared/components/match-history-strip/match-history-strip.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

type ActivityView = 'activity' | 'challenges';

const REFRESH_COOLDOWN_MS = 10000;

interface ActivityAccount extends AccountRecentGames {
  matches: ParticipantMatchHistory[];
}

@Component({
  selector: 'app-my-activity-page',
  imports: [
    PageShellComponent,
    ChallengeCardComponent,
    ChallengeListSkeletonComponent,
    PlayerIdentityComponent,
    MatchHistoryStripComponent,
    NavIconComponent,
    TranslatePipe,
  ],
  templateUrl: './my-activity-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './my-activity-page.component.scss',
})
export class MyActivityPageComponent implements OnInit, OnDestroy {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly i18n = inject(I18nService);

  protected readonly view = signal<ActivityView>('activity');

  protected readonly challenges = signal<ChallengeSummary[]>([]);
  protected readonly challengesLoading = signal(true);
  protected readonly challengesError = signal<string | null>(null);

  protected readonly activityAccounts = signal<ActivityAccount[]>([]);
  protected readonly activityLoading = signal(true);
  protected readonly activityError = signal<string | null>(null);
  protected readonly refreshCooldown = signal(false);
  private refreshCooldownTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.loadPage();
  }

  ngOnDestroy(): void {
    if (this.refreshCooldownTimer) {
      clearTimeout(this.refreshCooldownTimer);
    }
  }

  protected setView(view: ActivityView): void {
    this.view.set(view);
  }

  protected accountRankLabel(account: AccountRecentGames): string {
    return formatRankLabel(account.tier, account.rank, account.leaguePoints ?? 0, this.i18n.locale());
  }

  protected accountRankColor(account: AccountRecentGames): string {
    return tierColor(account.tier);
  }

  protected accountHasRecord(account: AccountRecentGames): boolean {
    return hasPlayedRecord(account.wins ?? 0, account.losses ?? 0);
  }

  protected accountWinRateLabel(account: AccountRecentGames): string {
    return winRateLabel(this.accountWinRate(account), account.wins ?? 0, account.losses ?? 0);
  }

  protected accountWinRateClass(account: AccountRecentGames): string {
    const tone = winRateToneModifier(this.accountWinRate(account), account.wins ?? 0, account.losses ?? 0);
    return `activity-account__winrate--${tone}`;
  }

  private accountWinRate(account: AccountRecentGames): number {
    const wins = account.wins ?? 0;
    const losses = account.losses ?? 0;
    const total = wins + losses;
    return total > 0 ? wins / total : 0;
  }

  protected refreshActivity(): void {
    if (this.refreshCooldown() || this.activityLoading()) {
      return;
    }
    this.loadActivity();
    this.refreshCooldown.set(true);
    this.refreshCooldownTimer = setTimeout(() => this.refreshCooldown.set(false), REFRESH_COOLDOWN_MS);
  }

  private async loadPage(): Promise<void> {
    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      const message = this.i18n.t('home.sessionExpired');
      this.challengesError.set(message);
      this.activityError.set(message);
      this.challengesLoading.set(false);
      this.activityLoading.set(false);
      return;
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    if (!this.auth.linkedAccount()) {
      this.challengesLoading.set(false);
      this.activityLoading.set(false);
      return;
    }

    void this.loadChallenges();
    void this.loadActivity();
  }

  private loadChallenges(): void {
    this.challengesLoading.set(true);
    this.challengeApi.listParticipatingChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.challengesLoading.set(false);
      },
      error: (err: { status?: number }) => {
        this.challengesError.set(
          err.status === 401 ? this.i18n.t('home.sessionExpired') : this.i18n.t('home.loadParticipatingError'),
        );
        this.challengesLoading.set(false);
      },
    });
  }

  private loadActivity(): void {
    this.activityLoading.set(true);
    this.challengeApi.listRecentGames().subscribe({
      next: (accounts) => {
        this.activityAccounts.set(
          accounts.map((account) => ({
            ...account,
            matches: account.games.map((game) => ({
              championId: game.championId,
              championIconUrl: game.championIconUrl,
              win: game.win,
              lpDelta: 0,
              playedAt: game.playedAt,
            })),
          })),
        );
        this.activityLoading.set(false);
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.activityError.set(this.i18n.t('home.sessionExpired'));
        } else if (err.status === 429) {
          this.activityError.set(this.i18n.t('errors.riotRateLimit'));
        } else {
          this.activityError.set(this.i18n.t('activity.loadError'));
        }
        this.activityLoading.set(false);
      },
    });
  }
}
