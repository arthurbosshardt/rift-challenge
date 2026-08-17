import { Component, ElementRef, HostListener, inject, input, signal, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { LoaderComponent } from '../loader/loader.component';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';

@Component({
  selector: 'app-page-shell',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, LoaderComponent, PlayerAvatarComponent],
  templateUrl: './page-shell.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './page-shell.component.scss',
})
export class PageShellComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');

  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly userMenuOpen = signal(false);
  private readonly userMenuRef = viewChild<ElementRef<HTMLElement>>('userMenu');
  private readonly router = inject(Router);

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

  protected async logout(): Promise<void> {
    this.closeUserMenu();
    await this.auth.logout();
    await this.router.navigateByUrl('/');
  }
}
