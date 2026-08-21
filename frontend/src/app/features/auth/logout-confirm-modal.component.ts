import { Component, effect, HostListener, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LogoutConfirmService } from '../../core/services/logout-confirm.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';

@Component({
  selector: 'app-logout-confirm-modal',
  imports: [TranslatePipe],
  templateUrl: './logout-confirm-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './logout-confirm-modal.component.scss',
})
export class LogoutConfirmModalComponent {
  protected readonly logoutConfirm = inject(LogoutConfirmService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      document.body.style.overflow = this.logoutConfirm.isOpen() ? 'hidden' : '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.logoutConfirm.isOpen()) {
      this.close();
    }
  }

  protected close(): void {
    this.logoutConfirm.close();
  }

  protected async confirm(): Promise<void> {
    this.close();
    await this.auth.logout();
    await this.router.navigateByUrl('/');
  }
}
