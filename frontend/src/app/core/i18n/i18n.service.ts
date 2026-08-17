import { Injectable, signal } from '@angular/core';
import { AppLocale, TRANSLATIONS, detectLocale, interpolate } from './translations';

const LOCALE_STORAGE_KEY = 'riftrace.locale';

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly locale = signal<AppLocale>(this.readInitialLocale());

  constructor() {
    this.syncDocumentLang(this.locale());
  }

  t(key: string, params?: Record<string, string | number>): string {
    const locale = this.locale();
    const template = TRANSLATIONS[locale][key] ?? TRANSLATIONS.fr[key] ?? key;
    return interpolate(template, params);
  }

  setLocale(locale: AppLocale): void {
    this.locale.set(locale);
    this.syncDocumentLang(locale);
    try {
      localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    } catch {
      /* ignore quota / private mode */
    }
  }

  private syncDocumentLang(locale: AppLocale): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.documentElement.lang = locale;
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
    if (typeof navigator === 'undefined') {
      return detectLocale(null);
    }
    return detectLocale(navigator.language, navigator.languages);
  }
}
