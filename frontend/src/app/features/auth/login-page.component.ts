import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, PageShellComponent, TranslatePipe],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
})
export class LoginPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(I18nService);

  protected mode: 'login' | 'signup' = 'login';
  protected email = '';
  protected password = '';
  protected username = '';
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly googleLoading = signal(false);

  ngOnInit(): void {
    const oauthError = this.route.snapshot.queryParamMap.get('error');
    if (oauthError) {
      this.error.set(oauthError);
    }
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

    await this.router.navigateByUrl('/my-races');
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
}
