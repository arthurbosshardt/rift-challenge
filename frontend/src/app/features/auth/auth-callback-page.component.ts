import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-auth-callback-page',
  imports: [TranslatePipe, SkeletonComponent],
  template: `
    <div class="callback" role="status" [attr.aria-label]="'auth.callback' | t" aria-live="polite">
      <div class="callback__skeleton">
        <app-skeleton width="3rem" height="3rem" radius="999px" />
        <app-skeleton height="1rem" maxWidth="12rem" />
        <app-skeleton height="0.875rem" maxWidth="8rem" />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      :host {
        display: flex;
        flex: 1;
        min-height: 100dvh;
      }

      .callback {
        display: flex;
        flex: 1;
        align-items: center;
        justify-content: center;
        width: 100%;
        padding: 2rem;
      }

      .callback__skeleton {
        display: grid;
        justify-items: center;
        gap: 0.75rem;
        width: min(100%, 16rem);
      }
    `,
  ],
})
export class AuthCallbackPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly authModal = inject(AuthModalService);
  private readonly router = inject(Router);

  async ngOnInit(): Promise<void> {
    const error = await this.auth.completeOAuthOrEmailCallback();
    if (error) {
      this.authModal.open({ error });
      await this.router.navigateByUrl('/challenges');
      return;
    }
    await this.router.navigateByUrl(this.auth.isAuthenticated() ? '/my-challenges' : '/challenges');
  }
}
