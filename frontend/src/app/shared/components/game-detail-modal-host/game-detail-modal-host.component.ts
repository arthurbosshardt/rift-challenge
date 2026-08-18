import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GameDetailModalComponent } from '../game-detail-modal/game-detail-modal.component';
import { GameDetailModalService } from '../../services/game-detail-modal.service';

@Component({
  selector: 'app-game-detail-modal-host',
  imports: [CommonModule, GameDetailModalComponent],
  template: `
    @if (modalService.isOpen() && modalService.game()) {
      <div
        class="modal-overlay"
        (click)="modalService.close()"
        role="dialog"
        aria-modal="true"
        aria-label="Game details"
      >
        <app-game-detail-modal
          [game]="modalService.game()!"
          (click)="$event.stopPropagation()"
        />
      </div>
    }
  `,
  styles: `
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.5);
      z-index: 1000;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameDetailModalHostComponent {
  readonly modalService = inject(GameDetailModalService);
}
