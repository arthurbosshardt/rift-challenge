import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class DeleteChallengeConfirmService {
  readonly isOpen = signal(false);
  readonly challengeName = signal('');

  private challengeId: string | null = null;
  private onConfirm: ((challengeId: string) => void) | null = null;

  open(challenge: { id: string; name: string }, onConfirm: (challengeId: string) => void): void {
    this.challengeId = challenge.id;
    this.challengeName.set(challenge.name);
    this.onConfirm = onConfirm;
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.challengeId = null;
    this.challengeName.set('');
    this.onConfirm = null;
  }

  confirm(): void {
    const id = this.challengeId;
    const callback = this.onConfirm;
    this.close();
    if (id && callback) {
      callback(id);
    }
  }
}
