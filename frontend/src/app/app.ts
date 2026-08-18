import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LoginModalComponent } from './features/auth/login-modal.component';
import { LogoutConfirmModalComponent } from './features/auth/logout-confirm-modal.component';
import { CreateChallengeModalComponent } from './features/challenge/create-challenge-modal.component';
import { CreatedChallengesModalComponent } from './features/home/created-challenges-modal.component';
import { DeleteChallengeConfirmModalComponent } from './features/challenge/delete-challenge-confirm-modal.component';
import { EditChallengeModalComponent } from './features/challenge/edit-challenge-modal.component';
import { SettingsModalComponent } from './features/settings/settings-modal.component';
import { ThemeService } from './core/theme/theme.service';
import { BackendStatusService } from './core/services/backend-status.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    LoginModalComponent,
    LogoutConfirmModalComponent,
    CreateChallengeModalComponent,
    CreatedChallengesModalComponent,
    EditChallengeModalComponent,
    DeleteChallengeConfirmModalComponent,
    SettingsModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template:
    '<router-outlet /><app-login-modal /><app-logout-confirm-modal /><app-create-challenge-modal /><app-created-challenges-modal /><app-edit-challenge-modal /><app-delete-challenge-confirm-modal /><app-settings-modal />',
})
export class App {
  constructor() {
    inject(ThemeService);
    inject(BackendStatusService).start();
  }
}
