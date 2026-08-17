import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LoginModalComponent } from './features/auth/login-modal.component';
import { LogoutConfirmModalComponent } from './features/auth/logout-confirm-modal.component';
import { CreateRaceModalComponent } from './features/race/create-race-modal.component';
import { EditRaceModalComponent } from './features/race/edit-race-modal.component';
import { ThemeService } from './core/theme/theme.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    LoginModalComponent,
    LogoutConfirmModalComponent,
    CreateRaceModalComponent,
    EditRaceModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template:
    '<router-outlet /><app-login-modal /><app-logout-confirm-modal /><app-create-race-modal /><app-edit-race-modal />',
})
export class App {
  constructor() {
    inject(ThemeService);
  }
}
