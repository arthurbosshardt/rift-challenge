import {
  Component,
  effect,
  HostListener,
  inject,
  signal,
  ChangeDetectionStrategy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalMode, AuthModalService } from '../../core/services/auth-modal.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-login-modal',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './login-modal.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login-modal.component.scss',
})
export class LoginModalComponent {
  protected readonly authModal = inject(AuthModalService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected mode: AuthModalMode = 'login';
  protected email = '';
  protected password = '';
  protected username = '';
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly googleLoading = signal(false);

  constructor() {
    effect(() => {
      if (this.authModal.isOpen()) {
        this.mode = this.authModal.initialMode();
        this.email = '';
        this.password = '';
        this.username = '';
        this.loading.set(false);
        this.googleLoading.set(false);
        this.error.set(this.authModal.initialError());
        document.body.style.overflow = 'hidden';
        return;
      }

      document.body.style.overflow = '';
      this.resetForm();
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.authModal.isOpen()) {
      this.close();
    }
  }

  protected close(): void {
    this.authModal.close();
  }

  protected setMode(mode: AuthModalMode): void {
    this.mode = mode;
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    this.error.set(null);
    this.loading.set(true);

    const message =
      this.mode === 'login'
        ? await this.auth.signInWithEmail(this.email.trim(), this.password)
        : await this.auth.signUpWithEmail(this.email.trim(), this.password, this.username.trim());

    this.loading.set(false);

    if (message) {
      this.error.set(message);
      return;
    }

    if (this.mode === 'signup') {
      this.error.set(this.i18n.t('auth.signupOk'));
      return;
    }

    await this.completeLogin();
  }

  protected async signInWithGoogle(): Promise<void> {
    this.error.set(null);
    this.googleLoading.set(true);
    const message = await this.auth.signInWithGoogle();
    this.googleLoading.set(false);
    if (message === 'auth.googleStartError') {
      this.error.set(this.i18n.t('auth.googleStartError'));
      return;
    }
    if (message) {
      this.error.set(message);
    }
  }

  private async completeLogin(): Promise<void> {
    const returnUrl = this.authModal.consumeReturnUrl();
    this.authModal.close();

    if (returnUrl) {
      await this.router.navigateByUrl(returnUrl);
      return;
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    const destination = this.auth.linkedAccount() ? '/my-challenges' : '/public-challenges';
    await this.router.navigateByUrl(destination);
  }

  private resetForm(): void {
    this.mode = 'login';
    this.email = '';
    this.password = '';
    this.username = '';
    this.error.set(null);
    this.loading.set(false);
    this.googleLoading.set(false);
  }
}
