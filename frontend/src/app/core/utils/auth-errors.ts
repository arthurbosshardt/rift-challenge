import { I18nService } from '../i18n/i18n.service';

type AuthErrorLike = {
  code?: string;
  message?: string;
};

export function authErrorToKey(error: AuthErrorLike | null | undefined): string | null {
  if (!error) {
    return null;
  }

  switch (error.code) {
    case 'invalid_credentials':
      return 'auth.invalidCredentials';
    case 'email_not_confirmed':
      return 'auth.emailNotConfirmed';
    case 'user_already_exists':
    case 'email_exists':
      return 'auth.userAlreadyRegistered';
    case 'weak_password':
      return 'auth.weakPassword';
    case 'over_email_send_rate_limit':
    case 'over_request_rate_limit':
      return 'auth.rateLimit';
    case 'same_password':
      return 'auth.samePassword';
    case 'validation_failed':
      return mapAuthMessageToKey(error.message) ?? 'auth.genericError';
    default:
      return mapAuthMessageToKey(error.message) ?? 'auth.genericError';
  }
}

export function translateAuthError(i18n: I18nService, message: string | null | undefined): string | null {
  if (!message) {
    return null;
  }

  if (message.startsWith('auth.')) {
    return i18n.t(message);
  }

  const key = mapAuthMessageToKey(message);
  return key ? i18n.t(key) : message;
}

function mapAuthMessageToKey(message: string | undefined): string | null {
  if (!message) {
    return null;
  }

  const normalized = message.trim().toLowerCase();

  if (normalized.includes('invalid login credentials')) {
    return 'auth.invalidCredentials';
  }
  if (normalized.includes('email not confirmed')) {
    return 'auth.emailNotConfirmed';
  }
  if (normalized.includes('user already registered') || normalized.includes('already been registered')) {
    return 'auth.userAlreadyRegistered';
  }
  if (normalized.includes('password should be at least') || normalized.includes('weak password')) {
    return 'auth.weakPassword';
  }
  if (normalized.includes('invalid email') || normalized.includes('unable to validate email')) {
    return 'auth.invalidEmail';
  }
  if (normalized.includes('rate limit') || normalized.includes('only request this once')) {
    return 'auth.rateLimit';
  }
  if (normalized.includes('same password')) {
    return 'auth.samePassword';
  }

  return null;
}
