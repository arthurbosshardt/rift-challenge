import { Injectable, signal } from '@angular/core';
import { AppLocale, TRANSLATIONS, detectLocale, interpolate } from './translations';

const LOCALE_STORAGE_KEY = 'riftrace.locale';

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly locale = signal<AppLocale>(this.readInitialLocale());

  t(key: string, params?: Record<string, string | number>): string {
    const locale = this.locale();
    const template = TRANSLATIONS[locale][key] ?? TRANSLATIONS.fr[key] ?? key;
    return interpolate(template, params);
  }

  setLocale(locale: AppLocale): void {
    this.locale.set(locale);
    try {
      localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    } catch {
      /* ignore quota / private mode */
    }
  }

  private readInitialLocale(): AppLocale {
    try {
      const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
      if (stored === 'fr' || stored === 'en') {
        return stored;
      }
    } catch {
      /* ignore */
    }
    return detectLocale(typeof navigator === 'undefined' ? null : navigator.language);
  }
}
