import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AuthModalService } from '../../../core/services/auth-modal.service';
import { CreateChallengeModalService } from '../../../core/services/create-challenge-modal.service';
import { CreatedChallengesModalService } from '../../../core/services/created-challenges-modal.service';
import { SettingsModalService } from '../../../core/services/settings-modal.service';
import { LogoutConfirmService } from '../../../core/services/logout-confirm.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { BrandLogoComponent } from '../brand-logo/brand-logo.component';
import { SkeletonComponent } from '../skeleton/skeleton.component';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';
import { SiteFooterComponent } from '../site-footer/site-footer.component';

@Component({
  selector: 'app-page-shell',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, BrandLogoComponent, SkeletonComponent, PlayerAvatarComponent, SiteFooterComponent],
  templateUrl: './page-shell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './page-shell.component.scss',
})
export class PageShellComponent {
  readonly title = input.required<string>();
  readonly centered = input(false);
  readonly brandTitle = input(false);
  readonly floatingNav = input(false);
  readonly showFooter = input(true);

  protected readonly auth = inject(AuthService);
  protected readonly authModal = inject(AuthModalService);
  protected readonly createChallengeModal = inject(CreateChallengeModalService);
  protected readonly createdChallengesModal = inject(CreatedChallengesModalService);
  protected readonly settingsModal = inject(SettingsModalService);
  protected readonly logoutConfirm = inject(LogoutConfirmService);
  protected readonly userMenuOpen = signal(false);
  protected readonly mobileNavOpen = signal(false);
  private readonly userMenuRef = viewChild<ElementRef<HTMLElement>>('userMenu');
  private readonly mobileNavRootRef = viewChild<ElementRef<HTMLElement>>('mobileNavRoot');

  @HostListener('document:click', ['$event'])
  protected closeMenusOnClickOutside(event: MouseEvent): void {
    const target = event.target as Node;

    if (this.userMenuOpen()) {
      const menu = this.userMenuRef()?.nativeElement;
      if (menu && !menu.contains(target)) {
        this.closeUserMenu();
      }
    }

    if (this.mobileNavOpen()) {
      const header = this.mobileNavRootRef()?.nativeElement;
      if (header && !header.contains(target)) {
        this.closeMobileNav();
      }
    }
  }

  @HostListener('document:keydown.escape')
  protected closeMenusOnEscape(): void {
    this.userMenuOpen.set(false);
    this.mobileNavOpen.set(false);
  }

  protected toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.closeMobileNav();
    this.userMenuOpen.update((open) => !open);
  }

  protected closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  protected toggleMobileNav(event: MouseEvent): void {
    event.stopPropagation();
    this.closeUserMenu();
    this.mobileNavOpen.update((open) => !open);
  }

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  protected openLoginFromMobileNav(): void {
    this.closeMobileNav();
    this.authModal.open();
  }

  protected openSettings(): void {
    this.closeUserMenu();
    this.settingsModal.open();
  }

  protected openCreatedChallenges(): void {
    this.closeUserMenu();
    this.createdChallengesModal.open();
  }

  protected openLogoutConfirm(): void {
    this.closeUserMenu();
    this.logoutConfirm.open();
  }
}
