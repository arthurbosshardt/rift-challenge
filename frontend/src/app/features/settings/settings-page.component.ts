import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UserRiotAccountApiService } from '../../core/services/user-riot-account-api.service';
import { AuthService } from '../../core/services/auth.service';
import { LinkedRiotAccount } from '../../core/models/race.models';
import { buildRiotId } from '../../core/utils/riot-id';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';
import { LoaderComponent } from '../../shared/components/loader/loader.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-settings-page',
  imports: [
    PageShellComponent,
    PlayerAvatarComponent,
    LoaderComponent,
    TranslatePipe,
    FormsModule,
    RouterLink,
  ],
  templateUrl: './settings-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './settings-page.component.scss',
})
export class SettingsPageComponent implements OnInit {
  private readonly accountApi = inject(UserRiotAccountApiService);
  protected readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  protected readonly linking = signal(false);
  protected readonly unlinking = signal(false);
  protected readonly accountError = signal<string | null>(null);
  protected readonly accountSuccess = signal(false);

  protected gameNameInput = '';
  protected tagLineInput = '';

  ngOnInit(): void {
    void this.auth.waitUntilReady().then(() => this.auth.refreshProfile());
  }

  protected linkedAccount(): LinkedRiotAccount | null {
    return this.auth.linkedAccount();
  }

  protected linkAccount(): void {
    if (this.linking()) {
      return;
    }

    const riotId = buildRiotId(this.gameNameInput, this.tagLineInput);
    if (!this.gameNameInput.trim()) {
      this.accountError.set(this.i18n.t('errors.gameNameRequired'));
      return;
    }
    if (!this.tagLineInput.trim()) {
      this.accountError.set(this.i18n.t('errors.tagLineRequired'));
      return;
    }
    if (!riotId) {
      this.accountError.set(this.i18n.t('errors.riotIdRequired'));
      return;
    }

    this.accountError.set(null);
    this.accountSuccess.set(false);
    this.linking.set(true);

    this.accountApi.linkAccount({ riotId }).subscribe({
      next: async () => {
        this.gameNameInput = '';
        this.tagLineInput = '';
        this.linking.set(false);
        this.accountSuccess.set(true);
        await this.auth.refreshProfile();
        window.setTimeout(() => this.accountSuccess.set(false), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.accountError.set(this.mapAccountError(err));
        this.linking.set(false);
      },
    });
  }

  protected unlinkAccount(): void {
    const account = this.linkedAccount();
    if (!account || this.unlinking()) {
      return;
    }

    this.accountError.set(null);
    this.accountSuccess.set(false);
    this.unlinking.set(true);

    this.accountApi.unlinkAccount(account.id).subscribe({
      next: async () => {
        this.unlinking.set(false);
        await this.auth.refreshProfile();
      },
      error: () => {
        this.accountError.set(this.i18n.t('accounts.unlinkError'));
        this.unlinking.set(false);
      },
    });
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
    return this.i18n.t('accounts.linkError');
  }
}
