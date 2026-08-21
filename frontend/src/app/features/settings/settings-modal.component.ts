import {
  Component,
  computed,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { UserRiotAccountApiService } from '../../core/services/user-riot-account-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { UserRiotAccount } from '../../core/models/challenge.models';
import { buildRiotId, parseRiotId } from '../../core/utils/riot-id';
import { translateAuthError } from '../../core/utils/auth-errors';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { SettingsAccountsSkeletonComponent } from '../../shared/components/settings-accounts-skeleton/settings-accounts-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { ThemeService } from '../../core/theme/theme.service';
import { Observable, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

const MAX_LINKED_ACCOUNTS = 5;

@Component({
  selector: 'app-settings-modal',
  imports: [
    NgTemplateOutlet,
    PlayerAvatarComponent,
    SettingsAccountsSkeletonComponent,
    TranslatePipe,
    FormsModule,
    NavIconComponent,
  ],
  templateUrl: './settings-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  private readonly pendingPrimaryAccountChange = signal<string | null>(null);

  protected readonly primaryAccount = computed(
    () => this.accounts().find((account) => account.primary) ?? null,
  );
  protected readonly smurfAccounts = computed(() =>
    this.accounts().filter((account) => !account.primary),
  );
  protected readonly canAddSmurf = computed(() => this.accounts().length < MAX_LINKED_ACCOUNTS);

  protected primaryRiotIdInput = '';
  protected smurfRiotIdInput = '';

  protected currentPasswordInput = '';
  protected newPasswordInput = '';
  protected newPasswordConfirmInput = '';
  protected readonly changingPassword = signal(false);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly passwordSuccess = signal(false);

  constructor() {
    effect(() => {
      if (this.settingsModal.isOpen()) {
        document.body.style.overflow = 'hidden';
        void this.auth.waitUntilReady().then(() => {
          this.loadAccounts(true).subscribe();
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

  protected loadAccounts(showSectionLoader = false): Observable<UserRiotAccount[]> {
    if (showSectionLoader || this.accounts().length === 0) {
      this.accountsLoading.set(true);
    }

    return this.accountApi.listAccounts().pipe(
      tap((accounts) => {
        this.accounts.set(accounts);
        this.accountsLoading.set(false);
      }),
      catchError(() => {
        this.accounts.set([]);
        this.accountsLoading.set(false);
        this.accountError.set(this.i18n.t('accounts.loadError'));
        return of([]);
      }),
    );
  }

  protected linkPrimaryAccount(): void {
    const primaryAccount = this.primaryAccount();
    
    // If primary account exists, unlink it first, then link the new one
    if (primaryAccount) {
      this.pendingPrimaryAccountChange.set(this.primaryRiotIdInput);
      this.unlinkAccount(primaryAccount);
    } else {
      this.linkAccount(this.primaryRiotIdInput, false, this.linkingPrimary, () => {
        this.primaryRiotIdInput = '';
      });
    }
  }

  protected linkSmurfAccount(): void {
    this.linkAccount(this.smurfRiotIdInput, true, this.linkingSmurf, () => {
      this.smurfRiotIdInput = '';
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
        
        // If there's a pending primary account change, load accounts and then link the new one
        const pending = this.pendingPrimaryAccountChange();
        if (pending) {
          this.pendingPrimaryAccountChange.set(null);
          // Wait for accounts to load before linking the new account
          this.loadAccounts().subscribe(() => {
            this.linkAccount(pending, false, this.linkingPrimary, () => {
              this.primaryRiotIdInput = '';
            });
          });
        } else {
          this.loadAccounts().subscribe();
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
    riotIdInput: string,
    smurf: boolean,
    linkingSignal: ReturnType<typeof signal<boolean>>,
    onSuccess: () => void,
  ): void {
    if (linkingSignal()) {
      return;
    }

    const parsed = parseRiotId(riotIdInput);
    
    if (!parsed) {
      this.accountError.set(this.i18n.t('errors.riotIdFormat'));
      return;
    }

    const gameName = parsed.gameName;
    const tagLine = parsed.tagLine;
    const riotId = buildRiotId(gameName, tagLine);

    if (!riotId) {
      this.accountError.set(this.i18n.t('errors.riotIdFormat'));
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
        this.loadAccounts().subscribe();
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

  protected async changePassword(): Promise<void> {
    this.passwordError.set(null);
    this.passwordSuccess.set(false);

    if (this.changingPassword()) {
      return;
    }

    if (!this.currentPasswordInput) {
      this.passwordError.set(this.i18n.t('settings.currentPasswordRequired'));
      return;
    }

    if (this.newPasswordInput !== this.newPasswordConfirmInput) {
      this.passwordError.set(this.i18n.t('auth.resetPasswordMismatch'));
      return;
    }

    this.changingPassword.set(true);
    const message = await this.auth.changePassword(this.currentPasswordInput, this.newPasswordInput);
    this.changingPassword.set(false);

    if (message) {
      this.passwordError.set(translateAuthError(this.i18n, message));
      return;
    }

    this.passwordSuccess.set(true);
    this.currentPasswordInput = '';
    this.newPasswordInput = '';
    this.newPasswordConfirmInput = '';
    window.setTimeout(() => this.passwordSuccess.set(false), 2500);
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
    this.primaryRiotIdInput = '';
    this.smurfRiotIdInput = '';
    this.currentPasswordInput = '';
    this.newPasswordInput = '';
    this.newPasswordConfirmInput = '';
    this.changingPassword.set(false);
    this.passwordError.set(null);
    this.passwordSuccess.set(false);
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
