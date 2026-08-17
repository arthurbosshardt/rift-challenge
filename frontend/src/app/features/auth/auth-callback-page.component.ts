import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { LoaderComponent } from '../../shared/components/loader/loader.component';

@Component({
  selector: 'app-auth-callback-page',
  imports: [TranslatePipe, LoaderComponent],
  template: `
    <div class="callback">
      <app-loader [label]="'auth.callback' | t" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .callback {
        padding: 2rem;
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
