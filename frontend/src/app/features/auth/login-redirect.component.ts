import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalService } from '../../core/services/auth-modal.service';

@Component({
  selector: 'app-login-redirect',
  template: '',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class LoginRedirectComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly authModal = inject(AuthModalService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  async ngOnInit(): Promise<void> {
    await this.auth.waitUntilReady();

    if (this.auth.isAuthenticated()) {
      await this.router.navigateByUrl('/my-races', { replaceUrl: true });
      return;
    }

    const error = this.route.snapshot.queryParamMap.get('error');
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? undefined;

    this.authModal.open({ error, returnUrl });
    await this.router.navigateByUrl('/public-races', { replaceUrl: true });
  }
}
