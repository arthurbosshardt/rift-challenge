import {
  Component,
  computed,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { UserRiotAccountApiService } from '../../core/services/user-riot-account-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { UserRiotAccount } from '../../core/models/challenge.models';
import { buildRiotId, normalizeGameName, normalizeTagLine } from '../../core/utils/riot-id';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { SettingsAccountsSkeletonComponent } from '../../shared/components/settings-accounts-skeleton/settings-accounts-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { ThemeService } from '../../core/theme/theme.service';

const MAX_LINKED_ACCOUNTS = 5;

@Component({
  selector: 'app-settings-modal',
  imports: [PlayerAvatarComponent, SettingsAccountsSkeletonComponent, TranslatePipe, FormsModule],
  templateUrl: './settings-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './settings-modal.component.scss',
})
export class SettingsModalComponent {
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly accountApi = inject(UserRiotAccountApiService);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly i18n = inject(I18nService);

  protected readonly accountsLoading = signal(true);
  protected readonly accounts = signal<UserRiotAccount[]>([]);
  protected readonly linkingPrimary = signal(false);
  protected readonly linkingSmurf = signal(false);
  protected readonly unlinkingAccountId = signal<string | null>(null);
  protected readonly accountError = signal<string | null>(null);
  protected readonly accountSuccess = signal(false);

  protected readonly primaryAccount = computed(
    () => this.accounts().find((account) => account.primary) ?? null,
  );
  protected readonly smurfAccounts = computed(() =>
    this.accounts().filter((account) => !account.primary),
  );
  protected readonly canAddSmurf = computed(() => this.accounts().length < MAX_LINKED_ACCOUNTS);

  protected primaryGameNameInput = '';
  protected primaryTagLineInput = '';
  protected smurfGameNameInput = '';
  protected smurfTagLineInput = '';

  constructor() {
    effect(() => {
      if (this.settingsModal.isOpen()) {
        document.body.style.overflow = 'hidden';
        void this.auth.waitUntilReady().then(() => {
          void this.auth.refreshProfile();
          this.loadAccounts(true);
        });
        return;
      }

      document.body.style.overflow = '';
      this.resetState();
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.settingsModal.isOpen()) {
      this.close();
    }
  }

  protected close(): void {
    this.settingsModal.close();
  }

  protected loadAccounts(showSectionLoader = false): void {
    if (showSectionLoader || this.accounts().length === 0) {
      this.accountsLoading.set(true);
    }

    this.accountApi.listAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.accountsLoading.set(false);
      },
      error: () => {
        this.accounts.set([]);
        this.accountsLoading.set(false);
        this.accountError.set(this.i18n.t('accounts.loadError'));
      },
    });
  }

  protected linkPrimaryAccount(): void {
    this.linkAccount(this.primaryGameNameInput, this.primaryTagLineInput, false, this.linkingPrimary, () => {
      this.primaryGameNameInput = '';
      this.primaryTagLineInput = '';
    });
  }

  protected linkSmurfAccount(): void {
    this.linkAccount(this.smurfGameNameInput, this.smurfTagLineInput, true, this.linkingSmurf, () => {
      this.smurfGameNameInput = '';
      this.smurfTagLineInput = '';
    });
  }

  protected unlinkAccount(account: UserRiotAccount): void {
    if (this.unlinkingAccountId()) {
      return;
    }

    this.accountError.set(null);
    this.accountSuccess.set(false);
    this.unlinkingAccountId.set(account.id);

    this.accountApi.unlinkAccount(account.id).subscribe({
      next: async () => {
        this.unlinkingAccountId.set(null);
        await this.auth.refreshProfile();
        this.loadAccounts();
      },
      error: () => {
        this.accountError.set(this.i18n.t('accounts.unlinkError'));
        this.unlinkingAccountId.set(null);
      },
    });
  }

  private linkAccount(
    gameNameInput: string,
    tagLineInput: string,
    smurf: boolean,
    linkingSignal: ReturnType<typeof signal<boolean>>,
    onSuccess: () => void,
  ): void {
    if (linkingSignal()) {
      return;
    }

    const gameName = normalizeGameName(gameNameInput);
    const tagLine = normalizeTagLine(tagLineInput);
    const riotId = buildRiotId(gameName, tagLine);

    if (!gameName) {
      this.accountError.set(this.i18n.t('errors.gameNameRequired'));
      return;
    }
    if (!tagLine) {
      this.accountError.set(this.i18n.t('errors.tagLineRequired'));
      return;
    }
    if (!riotId) {
      this.accountError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    this.accountError.set(null);
    this.accountSuccess.set(false);
    linkingSignal.set(true);

    this.accountApi.linkAccount({ riotId, smurf }).subscribe({
      next: async () => {
        onSuccess();
        linkingSignal.set(false);
        this.accountSuccess.set(true);
        await this.auth.refreshProfile();
        this.loadAccounts();
        window.setTimeout(() => this.accountSuccess.set(false), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.accountError.set(this.mapAccountError(err));
        linkingSignal.set(false);
      },
    });
  }

  private resetState(): void {
    this.accounts.set([]);
    this.accountsLoading.set(true);
    this.linkingPrimary.set(false);
    this.linkingSmurf.set(false);
    this.unlinkingAccountId.set(null);
    this.accountError.set(null);
    this.accountSuccess.set(false);
    this.primaryGameNameInput = '';
    this.primaryTagLineInput = '';
    this.smurfGameNameInput = '';
    this.smurfTagLineInput = '';
  }

  private mapAccountError(err: HttpErrorResponse): string {
    const message = typeof err.error?.message === 'string' ? err.error.message : '';

    if (err.status === 404 && message.includes('Riot account')) {
      return this.i18n.t('errors.riotNotFound');
    }
    if (err.status === 409 && message.includes('another user')) {
      return this.i18n.t('accounts.alreadyLinkedOther');
    }
    if (err.status === 409) {
      return this.i18n.t('accounts.alreadyLinked');
    }
    if (err.status === 400 && message.includes('gameName#tagLine')) {
      return this.i18n.t('errors.riotIdFormat');
    }
    if (err.status === 400 && message.includes('limit')) {
      return this.i18n.t('accounts.limitReached');
    }
    if (err.status === 400 && message.includes('smurfs')) {
      return this.i18n.t('accounts.primaryRequiredForSmurf');
    }
    if (err.status === 400 && message.includes('Primary account already linked')) {
      return this.i18n.t('accounts.useSmurfForm');
    }
    return this.i18n.t('accounts.linkError');
  }
}
