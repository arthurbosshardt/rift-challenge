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
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { SummonerTypeaheadComponent } from '../../shared/components/summoner-typeahead/summoner-typeahead.component';
import { SummonerSuggestion } from '../../core/services/summoner-search.service';
import { SettingsAccountsSkeletonComponent } from '../../shared/components/settings-accounts-skeleton/settings-accounts-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { ThemeService } from '../../core/theme/theme.service';

const MAX_LINKED_ACCOUNTS = 5;

@Component({
  selector: 'app-settings-modal',
  imports: [PlayerAvatarComponent, SettingsAccountsSkeletonComponent, TranslatePipe, FormsModule, SummonerTypeaheadComponent, NavIconComponent],
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
  protected readonly riotInvalidForm = signal<'primary' | 'smurf' | null>(null);
  private readonly pendingPrimaryAccountChange = signal<{
    gameName: string;
    tagLine: string;
  } | null>(null);

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
    const primaryAccount = this.primaryAccount();
    
    // If primary account exists, unlink it first, then link the new one
    if (primaryAccount) {
      this.pendingPrimaryAccountChange.set({
        gameName: this.primaryGameNameInput,
        tagLine: this.primaryTagLineInput,
      });
      this.unlinkAccount(primaryAccount);
    } else {
      this.linkAccount(this.primaryGameNameInput, this.primaryTagLineInput, false, this.linkingPrimary, () => {
        this.primaryGameNameInput = '';
        this.primaryTagLineInput = '';
      });
    }
  }

  protected linkSmurfAccount(): void {
    this.linkAccount(this.smurfGameNameInput, this.smurfTagLineInput, true, this.linkingSmurf, () => {
      this.smurfGameNameInput = '';
      this.smurfTagLineInput = '';
    });
  }

  protected applyPrimarySummoner(suggestion: SummonerSuggestion): void {
    this.primaryGameNameInput = suggestion.gameName;
    this.primaryTagLineInput = suggestion.tagLine;
  }

  protected applySmurfSummoner(suggestion: SummonerSuggestion): void {
    this.smurfGameNameInput = suggestion.gameName;
    this.smurfTagLineInput = suggestion.tagLine;
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
        
        // If there's a pending primary account change, link the new account
        const pending = this.pendingPrimaryAccountChange();
        if (pending) {
          this.pendingPrimaryAccountChange.set(null);
          // Wait a bit for the load to complete, then link
          setTimeout(() => {
            this.linkAccount(pending.gameName, pending.tagLine, false, this.linkingPrimary, () => {
              this.primaryGameNameInput = '';
              this.primaryTagLineInput = '';
            });
          }, 500);
        }
      },
      error: () => {
        this.accountError.set(this.i18n.t('accounts.unlinkError'));
        this.unlinkingAccountId.set(null);
        this.pendingPrimaryAccountChange.set(null);
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

    // Check if account is already linked
    const accountExists = this.accounts().some(
      (account) => account.riotId.toLowerCase() === riotId.toLowerCase(),
    );
    if (accountExists) {
      this.accountError.set(this.i18n.t('accounts.alreadyLinked'));
      return;
    }

    this.accountError.set(null);
    this.accountSuccess.set(false);
    this.riotInvalidForm.set(null);
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
        if (err.status === 404) {
          this.riotInvalidForm.set(smurf ? 'smurf' : 'primary');
        }
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
    this.riotInvalidForm.set(null);
    this.pendingPrimaryAccountChange.set(null);
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
