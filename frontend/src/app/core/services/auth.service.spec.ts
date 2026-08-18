import '@angular/compiler';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SESSION_LAST_SEEN_KEY, SESSION_TTL_MS } from '../auth/session-ttl';
import { apiUrl } from '../utils/api-url';
import type { AuthMeResponse, LinkedRiotAccount, UserRiotAccount } from '../models/challenge.models';

vi.mock('../../../environments/environment', () => ({
  environment: {
    supabaseUrl: 'https://supabase.test',
    supabasePublishableKey: 'pub-key',
    apiBaseUrl: 'https://api.test',
  },
}));

vi.mock('@angular/core', () => {
  const injectMock = vi.fn();
  const computedMock = vi.fn((getter: () => unknown) => getter);
  const signalMock = vi.fn(<T>(initial: T) => {
    let value = initial;
    const getter = (() => value) as (() => T) & { set: (next: T) => void; asReadonly: () => typeof getter };
    getter.set = (next: T) => {
      value = next;
    };
    getter.asReadonly = () => getter;
    return getter;
  });
  return {
    Injectable: () => (target: unknown) => target,
    inject: () => injectMock(),
    computed: computedMock,
    signal: signalMock,
    __mocks: { injectMock, computedMock, signalMock },
  };
});

const createClientMock = vi.fn();
vi.mock('@supabase/supabase-js', () => ({
  createClient: (...args: unknown[]) => createClientMock(...args),
}));

function createJwt(exp: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp })}.sig`;
}

type MockSession = {
  access_token: string;
  user?: {
    email?: string;
    user_metadata?: Record<string, unknown>;
  };
};

function createSession(overrides: Partial<MockSession> = {}): MockSession {
  return {
    access_token: createJwt(Math.floor(Date.now() / 1000) + 3600),
    user: {
      email: 'user@example.com',
      user_metadata: { username: 'fallback-user' },
    },
    ...overrides,
  };
}

describe('AuthService', async () => {
  const angularCore = await import('@angular/core');
  const { AuthService } = await import('./auth.service');
  const injectMock = (angularCore as typeof angularCore & { __mocks: { injectMock: ReturnType<typeof vi.fn> } }).__mocks.injectMock;

  let http: { get: ReturnType<typeof vi.fn> };
  let supabaseAuth: Record<string, ReturnType<typeof vi.fn>>;
  let authStateCallback: ((event: string, session: MockSession | null) => void) | undefined;
  let service: InstanceType<typeof AuthService>;
  let locationAssignSpy: ReturnType<typeof vi.spyOn>;
  let historyReplaceSpy: ReturnType<typeof vi.spyOn>;
  let addWindowListenerSpy: ReturnType<typeof vi.spyOn>;
  let addDocumentListenerSpy: ReturnType<typeof vi.spyOn>;
  let intervalSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-18T12:00:00Z'));
    localStorage.clear();

    http = {
      get: vi.fn(),
    };
    injectMock.mockReset();
    injectMock.mockReturnValue(http);

    authStateCallback = undefined;
    supabaseAuth = {
      getSession: vi.fn().mockResolvedValue({ data: { session: null } }),
      signOut: vi.fn().mockResolvedValue({}),
      onAuthStateChange: vi.fn((callback: typeof authStateCallback) => {
        authStateCallback = callback;
        return { data: { subscription: { unsubscribe: vi.fn() } } };
      }),
      exchangeCodeForSession: vi.fn(),
      refreshSession: vi.fn(),
      signInWithPassword: vi.fn(),
      signUp: vi.fn(),
      resetPasswordForEmail: vi.fn(),
      updateUser: vi.fn(),
      signInWithOAuth: vi.fn(),
    };
    createClientMock.mockReset();
    createClientMock.mockReturnValue({ auth: supabaseAuth });

    locationAssignSpy = vi.spyOn(window.location, 'assign').mockImplementation(() => {});
    historyReplaceSpy = vi.spyOn(window.history, 'replaceState').mockImplementation(() => {});
    addWindowListenerSpy = vi.spyOn(window, 'addEventListener');
    addDocumentListenerSpy = vi.spyOn(document, 'addEventListener');
    intervalSpy = vi.spyOn(window, 'setInterval');

    service = new AuthService();
    await service.waitUntilReady();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('initializes with no session and binds activity tracking once', () => {
    expect(createClientMock).toHaveBeenCalledWith('https://supabase.test', 'pub-key', expect.any(Object));
    expect(service.isAuthenticated()).toBe(false);
    expect(service.displayName()).toBeNull();
    expect(service.linkedAccount()).toBeNull();
    expect(addDocumentListenerSpy).toHaveBeenCalledWith('visibilitychange', expect.any(Function));
    expect(addWindowListenerSpy).toHaveBeenCalledWith('pagehide', expect.any(Function));
    expect(intervalSpy).toHaveBeenCalledOnce();
  });

  it('loads profile on initialization when a valid session exists', async () => {
    const linkedAccount: LinkedRiotAccount = {
      id: 'ra1',
      gameName: 'Summoner',
      tagLine: 'EUW',
      riotId: 'Summoner#EUW',
      profileIconId: 12,
    };
    const me: AuthMeResponse = { userId: 'u1', username: '  PlayerOne  ', linkedRiotAccount: linkedAccount };
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });
    http.get.mockReturnValueOnce(of(me));

    const initialized = new AuthService();
    await initialized.waitUntilReady();
    await vi.waitFor(() => expect(http.get).toHaveBeenCalledWith(apiUrl('/api/auth/me')));

    expect(initialized.isAuthenticated()).toBe(true);
    expect(initialized.displayName()).toBe('PlayerOne');
    expect(initialized.linkedAccount()).toEqual(linkedAccount);
    expect(initialized.headerIdentity()).toEqual({ label: 'Summoner', gameName: 'Summoner', profileIconId: 12 });
  });

  it('signs out expired initial sessions and clears last seen', async () => {
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now() - SESSION_TTL_MS - 1000));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });

    const initialized = new AuthService();
    await initialized.waitUntilReady();

    expect(supabaseAuth.signOut).toHaveBeenCalled();
    expect(initialized.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(SESSION_LAST_SEEN_KEY)).toBeNull();
  });

  it('completes oauth callback and loads profile', async () => {
    const session = createSession();
    const me: AuthMeResponse = { userId: 'u1', username: 'CallbackUser', linkedRiotAccount: null };
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, href: 'https://app.test/auth/callback?code=abc', origin: 'https://app.test', assign: locationAssignSpy } as Location);
    supabaseAuth.exchangeCodeForSession.mockResolvedValue({ data: { session }, error: null });
    http.get.mockReturnValueOnce(of(me)).mockReturnValueOnce(of([]));

    await expect(service.completeOAuthOrEmailCallback()).resolves.toBeNull();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.displayName()).toBe('CallbackUser');
  });

  it('returns mapped oauth callback error when exchange fails', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, href: 'https://app.test/auth/callback?access_token=token', origin: 'https://app.test', assign: locationAssignSpy } as Location);
    supabaseAuth.exchangeCodeForSession.mockResolvedValue({ data: { session: null }, error: { code: 'invalid_credentials', message: 'bad auth' } });

    await expect(service.completeOAuthOrEmailCallback()).resolves.toBe('auth.invalidCredentials');
    expect(service.isAuthenticated()).toBe(false);
  });

  it('returns cached access token when still valid', async () => {
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });
    const initialized = new AuthService();
    await initialized.waitUntilReady();

    await expect(initialized.resolveAccessToken()).resolves.toBe(session.access_token);
    expect(supabaseAuth.refreshSession).not.toHaveBeenCalled();
  });

  it('deduplicates in-flight access token refresh requests', async () => {
    const expiredToken = createJwt(Math.floor(Date.now() / 1000) - 10);
    const freshSession = createSession();
    const session = createSession({ access_token: expiredToken });
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } }).mockResolvedValue({ data: { session } });
    supabaseAuth.refreshSession.mockResolvedValue({ data: { session: freshSession }, error: null });

    const initialized = new AuthService();
    await initialized.waitUntilReady();

    const [first, second] = await Promise.all([initialized.resolveAccessToken(), initialized.resolveAccessToken()]);
    expect(first).toBe(freshSession.access_token);
    expect(second).toBe(freshSession.access_token);
    expect(supabaseAuth.refreshSession).toHaveBeenCalledTimes(1);
  });

  it('peekAccessToken returns null for missing or expired session', async () => {
    expect(service.peekAccessToken()).toBeNull();

    const expiredSession = createSession({ access_token: createJwt(Math.floor(Date.now() / 1000) - 10) });
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session: expiredSession } });
    const initialized = new AuthService();
    await initialized.waitUntilReady();

    expect(initialized.peekAccessToken()).toBeNull();
  });

  it('signs in with email and loads profile on success', async () => {
    const session = createSession();
    supabaseAuth.signInWithPassword.mockResolvedValue({ data: { session }, error: null });
    http.get.mockReturnValueOnce(of({ userId: 'u1', username: 'EmailUser', linkedRiotAccount: null })).mockReturnValueOnce(of([]));

    await expect(service.signInWithEmail('a@test.dev', 'secret')).resolves.toBeNull();
    expect(supabaseAuth.signInWithPassword).toHaveBeenCalledWith({ email: 'a@test.dev', password: 'secret' });
    expect(service.displayName()).toBe('EmailUser');
  });

  it('returns auth errors for email sign in, sign up and reset', async () => {
    supabaseAuth.signInWithPassword.mockResolvedValue({ data: { session: null }, error: { code: 'invalid_credentials', message: 'bad' } });
    supabaseAuth.signUp.mockResolvedValue({ error: { code: 'user_already_exists', message: 'exists' } });
    supabaseAuth.resetPasswordForEmail.mockResolvedValue({ error: { code: 'over_request_rate_limit', message: 'slow down' } });

    await expect(service.signInWithEmail('a@test.dev', 'bad')).resolves.toBe('auth.invalidCredentials');
    await expect(service.signUpWithEmail('a@test.dev', 'secret', 'user')).resolves.toBe('auth.userAlreadyRegistered');
    await expect(service.requestPasswordReset('a@test.dev')).resolves.toBe('auth.rateLimit');
  });

  it('handles password recovery callback success and invalid links', async () => {
    const session = createSession();
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, href: 'https://app.test/auth/reset-password?code=abc', origin: 'https://app.test', assign: locationAssignSpy } as Location);
    supabaseAuth.exchangeCodeForSession.mockResolvedValueOnce({ data: { session }, error: null });

    await expect(service.completePasswordRecoveryCallback()).resolves.toBeNull();
    expect(historyReplaceSpy).toHaveBeenCalledWith({}, '', 'https://app.test/auth/reset-password');

    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, href: 'https://app.test/auth/reset-password', origin: 'https://app.test', assign: locationAssignSpy } as Location);
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session: null } });
    await expect(service.completePasswordRecoveryCallback()).resolves.toBe('auth.resetLinkInvalid');
  });

  it('updates password and refreshes profile only on success', async () => {
    supabaseAuth.updateUser.mockResolvedValueOnce({ error: null }).mockResolvedValueOnce({ error: { code: 'same_password', message: 'same password' } });
    const refreshSpy = vi.spyOn(service as never, 'loadProfile').mockResolvedValue(undefined);

    await expect(service.updatePassword('new-secret')).resolves.toBeNull();
    await expect(service.updatePassword('new-secret')).resolves.toBe('auth.samePassword');
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  it('starts google oauth and handles missing url or errors', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, origin: 'https://app.test', assign: locationAssignSpy } as Location);
    supabaseAuth.signInWithOAuth
      .mockResolvedValueOnce({ data: { url: 'https://oauth.test/start' }, error: null })
      .mockResolvedValueOnce({ data: { url: null }, error: null })
      .mockResolvedValueOnce({ data: { url: null }, error: { code: 'validation_failed', message: 'rate limit' } });

    await expect(service.signInWithGoogle()).resolves.toBeNull();
    expect(locationAssignSpy).toHaveBeenCalledWith('https://oauth.test/start?redirect_to=https%3A%2F%2Fapp.test%2Fauth%2Fcallback');
    await expect(service.signInWithGoogle()).resolves.toBe('auth.googleStartError');
    await expect(service.signInWithGoogle()).resolves.toBe('auth.rateLimit');
  });

  it('falls back to riot accounts when auth/me has no linked account', async () => {
    const linkedAccounts: UserRiotAccount[] = [
      { id: '1', gameName: 'Alt', tagLine: 'EUW', riotId: 'Alt#EUW', profileIconId: 1, primary: false },
      { id: '2', gameName: 'Main', tagLine: 'EUW', riotId: 'Main#EUW', profileIconId: 2, primary: true },
    ];
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });
    http.get.mockReturnValueOnce(of({ userId: 'u1', username: 'Fallback', linkedRiotAccount: null })).mockReturnValueOnce(of(linkedAccounts));

    const initialized = new AuthService();
    await initialized.waitUntilReady();
    await vi.waitFor(() => expect(initialized.linkedAccount()).toEqual({
      id: '2',
      gameName: 'Main',
      tagLine: 'EUW',
      riotId: 'Main#EUW',
      profileIconId: 2,
    }));
  });

  it('clears profile state when profile loading fails', async () => {
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });
    http.get.mockReturnValueOnce(throwError(() => ({ status: 401 }))); 

    const initialized = new AuthService();
    await initialized.waitUntilReady();
    await vi.waitFor(() => expect(initialized.displayName()).toBeNull());
    expect(initialized.linkedAccount()).toBeNull();
  });

  it('handles profile and fallback errors by clearing profile state', async () => {
    const session = createSession();
    localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    supabaseAuth.getSession.mockResolvedValueOnce({ data: { session } });
    http.get.mockReturnValueOnce(of({ userId: 'u1', username: ' ', linkedRiotAccount: null })).mockReturnValueOnce(throwError(() => new Error('network')));

    const initialized = new AuthService();
    await initialized.waitUntilReady();
    await vi.waitFor(() => expect(initialized.displayName()).toBeNull());
    expect(initialized.linkedAccount()).toBeNull();
    expect(initialized.headerIdentity()).toEqual({ label: 'user@example.com', gameName: 'user@example.com', profileIconId: null });
  });

  it('reacts to auth state changes for sign in and sign out', async () => {
    expect(authStateCallback).toBeTypeOf('function');
    http.get.mockReturnValueOnce(of({ userId: 'u1', username: 'StateUser', linkedRiotAccount: null })).mockReturnValueOnce(of([]));

    authStateCallback?.('SIGNED_IN', createSession());
    await vi.waitFor(() => expect(service.displayName()).toBe('StateUser'));

    authStateCallback?.('SIGNED_OUT', null);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.displayName()).toBeNull();
  });
});
