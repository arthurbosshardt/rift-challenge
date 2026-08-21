import { Component, effect, HostListener, inject, ChangeDetectionStrategy } from '@angular/core';
import { DeleteChallengeConfirmService } from '../../core/services/delete-challenge-confirm.service';
import { TranslatePipe } from '../../core/i18n/t.pipe';

@Component({
  selector: 'app-delete-challenge-confirm-modal',
  imports: [TranslatePipe],
  templateUrl: './delete-challenge-confirm-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './delete-challenge-confirm-modal.component.scss',
})
export class DeleteChallengeConfirmModalComponent {
  protected readonly deleteChallengeConfirm = inject(DeleteChallengeConfirmService);

  constructor() {
    effect(() => {
      document.body.style.overflow = this.deleteChallengeConfirm.isOpen() ? 'hidden' : '';
    });
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.deleteChallengeConfirm.isOpen()) {
      this.close();
    }
  }

  protected close(): void {
    this.deleteChallengeConfirm.close();
  }

  protected confirm(): void {
    this.deleteChallengeConfirm.confirm();
  }
}
