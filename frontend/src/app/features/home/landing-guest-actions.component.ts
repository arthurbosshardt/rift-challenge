import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthModalService } from '../../core/services/auth-modal.service';
import { BackendStatusService } from '../../core/services/backend-status.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { NavIconComponent } from '../../shared/components/nav-icon/nav-icon.component';

@Component({
  selector: 'app-landing-guest-actions',
  imports: [RouterLink, TranslatePipe, NavIconComponent],
  templateUrl: './landing-guest-actions.component.html',
  styleUrl: './landing-guest-actions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingGuestActionsComponent {
  protected readonly backend = inject(BackendStatusService);
  protected readonly authModal = inject(AuthModalService);
}
