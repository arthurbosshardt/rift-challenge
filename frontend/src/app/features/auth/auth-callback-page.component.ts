import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-auth-callback-page',
  template: '<p class="state">Connexion en cours…</p>',
  styles: [
    `
      .state {
        padding: 2rem;
        color: var(--text-muted);
      }
    `,
  ],
})
export class AuthCallbackPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  async ngOnInit(): Promise<void> {
    const error = await this.auth.completeOAuthOrEmailCallback();
    if (error) {
      await this.router.navigate(['/login'], { queryParams: { error } });
      return;
    }
    await this.router.navigateByUrl(this.auth.isAuthenticated() ? '/my-races' : '/login');
  }
}
