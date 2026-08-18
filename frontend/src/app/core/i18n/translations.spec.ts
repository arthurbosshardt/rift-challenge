import { describe, expect, it } from 'vitest';
import { detectLocale, interpolate, TRANSLATIONS } from './translations';

describe('i18n translations', () => {
  it('detects French from browser language', () => {
    expect(detectLocale('fr-FR')).toBe('fr');
    expect(detectLocale('fr')).toBe('fr');
    expect(detectLocale('fr_CA')).toBe('fr');
    expect(detectLocale(null, ['fr-FR', 'en'])).toBe('fr');
  });

  it('falls back to English for other languages', () => {
    expect(detectLocale('en-US')).toBe('en');
    expect(detectLocale('de-DE')).toBe('en');
    expect(detectLocale(null)).toBe('en');
    expect(detectLocale(null, ['en-GB'])).toBe('en');
  });

  it('interpolates named placeholders', () => {
    expect(interpolate('{name} played SoloQ', { name: 'JohnDoe#EUW' })).toBe('JohnDoe#EUW played SoloQ');
  });

  it('keeps French and English keys in sync', () => {
    expect(TRANSLATIONS.fr['landing.introBrand']).toBe('Rift Challenge');
    expect(TRANSLATIONS.en['landing.introBrand']).toBe('Rift Challenge');
    expect(Object.keys(TRANSLATIONS.en).sort()).toEqual(Object.keys(TRANSLATIONS.fr).sort());
  });
});
