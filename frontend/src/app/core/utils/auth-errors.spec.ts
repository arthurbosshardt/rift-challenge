import { describe, expect, it } from 'vitest';
import { I18nService } from '../i18n/i18n.service';
import { authErrorToKey, translateAuthError } from './auth-errors';

describe('auth-errors', () => {
  const i18n = new I18nService();
  i18n.setLocale('fr');

  it('maps invalid login credentials to French message', () => {
    const key = authErrorToKey({ code: 'invalid_credentials', message: 'Invalid login credentials' });
    expect(key).toBe('auth.invalidCredentials');
    expect(translateAuthError(i18n, key)).toBe('Email ou mot de passe incorrect.');
  });

  it('maps raw Supabase message when code is missing', () => {
    expect(translateAuthError(i18n, 'Invalid login credentials')).toBe('Email ou mot de passe incorrect.');
  });
});
