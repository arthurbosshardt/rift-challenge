import { Injectable, signal } from '@angular/core';

export type AppTheme = 'dark' | 'light';

const THEME_STORAGE_KEY = 'riftchallenge.theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<AppTheme>(this.readInitialTheme());

  constructor() {
    this.applyTheme(this.theme());
  }

  setTheme(theme: AppTheme): void {
    this.theme.set(theme);
    this.applyTheme(theme);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      /* ignore quota / private mode */
    }
  }

  private applyTheme(theme: AppTheme): void {
    if (typeof document === 'undefined') {
      return;
    }

    document.documentElement.dataset['theme'] = theme;
  }

  private readInitialTheme(): AppTheme {
    try {
      const stored = localStorage.getItem(THEME_STORAGE_KEY) ?? localStorage.getItem('riftrace.theme');
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      /* ignore */
    }

    return 'dark';
  }
}
