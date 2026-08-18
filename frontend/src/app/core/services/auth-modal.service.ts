import { Injectable, signal } from '@angular/core';

export type AuthModalMode = 'login' | 'signup' | 'forgot-password';

export interface AuthModalOpenOptions {
  returnUrl?: string;
  error?: string | null;
  mode?: AuthModalMode;
}

@Injectable({ providedIn: 'root' })
export class AuthModalService {
  readonly isOpen = signal(false);
  readonly returnUrl = signal<string | null>(null);
  readonly initialError = signal<string | null>(null);
  readonly initialMode = signal<AuthModalMode>('login');

  open(options: AuthModalOpenOptions = {}): void {
    this.returnUrl.set(options.returnUrl ?? null);
    this.initialError.set(options.error ?? null);
    this.initialMode.set(options.mode ?? 'login');
    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.returnUrl.set(null);
    this.initialError.set(null);
  }

  consumeReturnUrl(): string | null {
    const url = this.returnUrl();
    this.returnUrl.set(null);
    return url;
  }
}
