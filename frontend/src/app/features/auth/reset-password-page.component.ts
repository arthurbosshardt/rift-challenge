import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { translateAuthError } from '../../core/utils/auth-errors';
import { BrandLogoComponent } from '../../shared/components/brand-logo/brand-logo.component';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-reset-password-page',
  imports: [
    FormsModule,
    TranslatePipe,
    BrandLogoComponent,
    NavIconComponent,
    SkeletonComponent,
  ],
  templateUrl: './reset-password-page.component.html',
  styleUrl: './reset-password-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly authModal = inject(AuthModalService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected password = '';
  protected passwordConfirm = '';
  protected readonly verifying = signal(true);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly linkError = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const message = await this.auth.completePasswordRecoveryCallback();
    this.verifying.set(false);

    if (message === 'auth.resetLinkInvalid') {
      this.linkError.set(this.i18n.t('auth.resetLinkInvalid'));
      return;
    }

    if (message) {
      this.linkError.set(translateAuthError(this.i18n, message));
    }
  }

  protected openLogin(): void {
    this.authModal.open({ mode: 'forgot-password' });
    void this.router.navigateByUrl('/public-challenges');
  }

  protected async submit(): Promise<void> {
    this.error.set(null);
    this.success.set(null);

    if (this.password !== this.passwordConfirm) {
      this.error.set(this.i18n.t('auth.resetPasswordMismatch'));
      return;
    }

    this.loading.set(true);
    const message = await this.auth.updatePassword(this.password);
    this.loading.set(false);

    if (message) {
      this.error.set(translateAuthError(this.i18n, message));
      return;
    }

    this.success.set(this.i18n.t('auth.resetPasswordOk'));
    await this.auth.refreshProfile();

    const destination = this.auth.linkedAccount() ? '/my-challenges' : '/public-challenges';
    window.setTimeout(() => {
      void this.router.navigateByUrl(destination);
    }, 1200);
  }
}
