import { Component, ElementRef, HostListener, inject, input, signal, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AuthModalService } from '../../../core/services/auth-modal.service';
import { CreateRaceModalService } from '../../../core/services/create-race-modal.service';
import { LogoutConfirmService } from '../../../core/services/logout-confirm.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { BrandLogoComponent } from '../brand-logo/brand-logo.component';
import { LoaderComponent } from '../loader/loader.component';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { SiteFooterComponent } from '../site-footer/site-footer.component';

@Component({
  selector: 'app-page-shell',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, BrandLogoComponent, LoaderComponent, PlayerAvatarComponent, SiteFooterComponent],
  templateUrl: './page-shell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './page-shell.component.scss',
})
export class PageShellComponent {
  readonly title = input.required<string>();
  readonly centered = input(false);

  protected readonly auth = inject(AuthService);
  protected readonly authModal = inject(AuthModalService);
  protected readonly createRaceModal = inject(CreateRaceModalService);
  protected readonly logoutConfirm = inject(LogoutConfirmService);
  protected readonly i18n = inject(I18nService);
  protected readonly userMenuOpen = signal(false);
  private readonly userMenuRef = viewChild<ElementRef<HTMLElement>>('userMenu');

  @HostListener('document:click', ['$event'])
  protected closeUserMenuOnClickOutside(event: MouseEvent): void {
    if (!this.userMenuOpen()) {
      return;
    }
    const menu = this.userMenuRef()?.nativeElement;
    if (menu && !menu.contains(event.target as Node)) {
      this.closeUserMenu();
    }
  }

  @HostListener('document:keydown.escape')
  protected closeUserMenuOnEscape(): void {
    this.userMenuOpen.set(false);
  }

  protected toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.userMenuOpen.update((open) => !open);
  }

  protected closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  protected openLogoutConfirm(): void {
    this.closeUserMenu();
    this.logoutConfirm.open();
  }
}
