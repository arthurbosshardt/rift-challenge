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
import { NavIconComponent } from '../nav-icon/nav-icon.component';
import { SiteFooterComponent } from '../site-footer/site-footer.component';

@Component({
  selector: 'app-page-shell',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, BrandLogoComponent, SkeletonComponent, PlayerAvatarComponent, NavIconComponent, SiteFooterComponent],
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
  private readonly userMenuRef = viewChild<ElementRef<HTMLElement>>('userMenu');

  @HostListener('document:click', ['$event'])
  protected closeMenusOnClickOutside(event: MouseEvent): void {
    if (!this.userMenuOpen()) {
      return;
    }

    const menu = this.userMenuRef()?.nativeElement;
    const target = event.target as Node;
    if (menu && !menu.contains(target)) {
      this.closeUserMenu();
    }
  }

  @HostListener('document:keydown.escape')
  protected closeMenusOnEscape(): void {
    this.userMenuOpen.set(false);
  }

  protected toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.userMenuOpen.update((open) => !open);
  }

  protected closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  protected openLoginFromMenu(): void {
    this.closeUserMenu();
    this.authModal.open();
  }

  protected openCreateFromMenu(): void {
    this.closeUserMenu();
    this.createChallengeModal.open();
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
