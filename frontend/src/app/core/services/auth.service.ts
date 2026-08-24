import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ApplicationRef, Injectable, computed, inject, signal } from '@angular/core';
import { createClient, Session, SupabaseClient } from '@supabase/supabase-js';
import { firstValueFrom, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthMeResponse, LinkedRiotAccount, UserRiotAccount } from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';
import {
  SESSION_LAST_SEEN_KEY,
  SESSION_TTL_MS,
  isSessionExpired,
  parseLastSeen,
} from '../auth/session-ttl';
import { authErrorToKey } from '../utils/auth-errors';
import { ActivityCacheService } from './activity-cache.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly activityCache = inject(ActivityCacheService);
  private readonly appRef = inject(ApplicationRef);

  private readonly supabase: SupabaseClient = createClient(
    environment.supabaseUrl,
    environment.supabasePublishableKey,
    {
      auth: {
        detectSessionInUrl: true,
        flowType: 'implicit',
        persistSession: true,
        autoRefreshToken: true,
      },
    },
  );

  private readonly session = signal<Session | null>(null);
  private readonly profileUsername = signal<string | null>(null);
  private readonly linkedRiotAccount = signal<LinkedRiotAccount | null>(null);
  private readonly profileLoading = signal(false);
  private readyResolve: (() => void) | null = null;
  private readonly ready = new Promise<void>((resolve) => {
    this.readyResolve = resolve;
  });
  private profileLoad: Promise<void> | null = null;
  private accessTokenInFlight: Promise<string | null> | null = null;
  private activityBound = false;

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly userId = computed(() => this.session()?.user?.id ?? null);
  readonly displayName = computed(() => this.profileUsername());
  readonly email = computed(() => this.session()?.user?.email ?? null);
  readonly linkedAccount = computed(() => this.linkedRiotAccount());
  readonly headerIdentity = computed(() => {
    const linked = this.linkedRiotAccount();
    if (linked) {
      return {
        label: linked.gameName,
        gameName: linked.gameName,
        profileIconId: linked.profileIconId,
      };
    }
    const username = this.profileUsername();
    if (username) {
      return { label: username, gameName: username, profileIconId: null as number | null };
    }
    const sessionUser = this.session()?.user;
    const fallback =
      (typeof sessionUser?.user_metadata?.['username'] === 'string'
        ? sessionUser.user_metadata['username']
        : null) ??
      sessionUser?.email ??
      null;
    return fallback
      ? { label: fallback, gameName: fallback, profileIconId: null as number | null }
      : null;
  });
  readonly isProfileLoading = computed(() => this.profileLoading());

  constructor() {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    try {
      const { data } = await this.supabase.auth.getSession();
      const validSession = this.sessionWithinTtl(data.session) ? data.session : null;

      if (data.session && !validSession) {
        await this.supabase.auth.signOut();
        this.clearLastSeen();
      }

      this.session.set(validSession);
      if (validSession) {
        this.touchLastSeen();
        void this.loadProfile();
      }
      this.bindActivityTracking();

      this.supabase.auth.onAuthStateChange((event, session) => {
        if (event === 'SIGNED_OUT' || !session) {
          this.session.set(null);
          this.profileUsername.set(null);
          this.linkedRiotAccount.set(null);
          this.profileLoading.set(false);
          this.clearLastSeen();
          return;
        }

        if (event === 'SIGNED_IN' || event === 'TOKEN_REFRESHED' || event === 'USER_UPDATED') {
          this.session.set(session);
          this.touchLastSeen();
          void this.loadProfile();
          return;
        }

        if (!this.sessionWithinTtl(session, true)) {
          void this.logout();
          return;
        }
        this.session.set(session);
        this.touchLastSeen();
        void this.loadProfile();
      });
    } finally {
      this.readyResolve?.();
    }
  }

  async waitUntilReady(): Promise<void> {
    await this.ready;
  }

  async completeOAuthOrEmailCallback(): Promise<string | null> {
    await this.waitUntilReady();

    const callbackUrl = window.location.href;
    const hasAuthCode = new URL(callbackUrl).searchParams.has('code');

    if (hasAuthCode) {
      const { data, error } = await this.supabase.auth.exchangeCodeForSession(callbackUrl);
      if (error) {
        this.session.set(null);
        return authErrorToKey(error) ?? error.message;
      }
      this.touchLastSeen();
      this.session.set(data.session);
      if (data.session) {
        await this.loadProfile();
      }
      return null;
    }

    // Flow implicit : detectSessionInUrl a déjà consommé le fragment #access_token
    // pendant initialize(), quel que soit le navigateur/appareil ayant ouvert le lien.
    const { data } = await this.supabase.auth.getSession();
    this.session.set(data.session);
    return null;
  }

  async resolveAccessToken(): Promise<string | null> {
    if (this.session() && !this.sessionWithinTtl(this.session())) {
      await this.logout();
      return null;
    }

    const cached = this.session();
    if (cached?.access_token && !this.isAccessTokenExpired(cached.access_token)) {
      this.touchLastSeen();
      return cached.access_token;
    }

    if (this.accessTokenInFlight) {
      return this.accessTokenInFlight;
    }

    this.accessTokenInFlight = this.fetchAccessTokenFromSupabase().finally(() => {
      this.accessTokenInFlight = null;
    });
    return this.accessTokenInFlight;
  }

  /** Token en mémoire sans appel réseau Supabase (fallback interceptor). */
  peekAccessToken(): string | null {
    const session = this.session();
    if (!session?.access_token || !this.sessionWithinTtl(session)) {
      return null;
    }
    return session.access_token;
  }

  private async fetchAccessTokenFromSupabase(): Promise<string | null> {
    const { data } = await this.supabase.auth.getSession();
    const session = data.session;

    if (!session?.access_token) {
      const refreshed = await this.supabase.auth.refreshSession();
      if (refreshed.error || !refreshed.data.session?.access_token) {
        this.session.set(null);
        return null;
      }
      if (!this.sessionWithinTtl(refreshed.data.session)) {
        await this.logout();
        return null;
      }
      this.session.set(refreshed.data.session);
      this.touchLastSeen();
      return refreshed.data.session.access_token;
    }

    if (!this.sessionWithinTtl(session)) {
      await this.logout();
      return null;
    }

    if (!this.isAccessTokenExpired(session.access_token)) {
      this.session.set(session);
      this.touchLastSeen();
      return session.access_token;
    }

    const refreshed = await this.supabase.auth.refreshSession();
    if (refreshed.error || !refreshed.data.session?.access_token) {
      await this.logout();
      return null;
    }
    if (!this.sessionWithinTtl(refreshed.data.session)) {
      await this.logout();
      return null;
    }

    this.session.set(refreshed.data.session);
    this.touchLastSeen();
    return refreshed.data.session.access_token;
  }

  async signInWithEmail(email: string, password: string): Promise<string | null> {
    const { data, error } = await this.supabase.auth.signInWithPassword({ email, password });
    if (!error) {
      this.touchLastSeen();
      this.session.set(data.session);
      if (data.session) {
        await this.loadProfile();
      }
    }
    return authErrorToKey(error) ?? error?.message ?? null;
  }

  async signUpWithEmail(email: string, password: string, username: string): Promise<string | null> {
    const { error } = await this.supabase.auth.signUp({
      email,
      password,
      options: {
        data: { username },
        emailRedirectTo: `${window.location.origin}/auth/callback`,
      },
    });
    return authErrorToKey(error) ?? error?.message ?? null;
  }

  async requestPasswordReset(email: string): Promise<string | null> {
    const { error } = await this.supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/auth/reset-password`,
    });
    return authErrorToKey(error) ?? error?.message ?? null;
  }

  async completePasswordRecoveryCallback(): Promise<string | null> {
    await this.waitUntilReady();

    const callbackUrl = window.location.href;
    const hasRecoveryCode = new URL(callbackUrl).searchParams.has('code');

    if (hasRecoveryCode) {
      const { data, error } = await this.supabase.auth.exchangeCodeForSession(callbackUrl);
      if (error) {
        this.session.set(null);
        return authErrorToKey(error) ?? error.message;
      }
      this.touchLastSeen();
      this.session.set(data.session);
      window.history.replaceState({}, '', `${window.location.origin}/auth/reset-password`);
      return null;
    }

    // Flow implicit : detectSessionInUrl a déjà consommé le fragment #access_token
    // pendant initialize(), quel que soit le navigateur/appareil ayant ouvert le lien.
    const { data } = await this.supabase.auth.getSession();
    if (data.session) {
      this.session.set(data.session);
      window.history.replaceState({}, '', `${window.location.origin}/auth/reset-password`);
      return null;
    }

    return 'auth.resetLinkInvalid';
  }

  async updatePassword(newPassword: string): Promise<string | null> {
    const { error } = await this.supabase.auth.updateUser({ password: newPassword });
    if (!error) {
      await this.loadProfile();
    }
    return authErrorToKey(error) ?? error?.message ?? null;
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<string | null> {
    const email = this.session()?.user?.email;
    if (!email) {
      return 'auth.genericError';
    }

    const { error: reauthError } = await this.supabase.auth.signInWithPassword({
      email,
      password: currentPassword,
    });
    if (reauthError) {
      return reauthError.code === 'invalid_credentials'
        ? 'settings.currentPasswordInvalid'
        : (authErrorToKey(reauthError) ?? reauthError.message);
    }

    return this.updatePassword(newPassword);
  }

  async signInWithGoogle(): Promise<string | null> {
    const redirectTo = `${window.location.origin}/auth/callback`;
    const { data, error } = await this.supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo,
        skipBrowserRedirect: true,
      },
    });
    if (error) {
      return authErrorToKey(error) ?? error.message;
    }
    if (!data.url) {
      return 'auth.googleStartError';
    }

    const oauthUrl = new URL(data.url);
    oauthUrl.searchParams.set('redirect_to', redirectTo);
    window.location.assign(oauthUrl.toString());
    return null;
  }

  async refreshProfile(): Promise<void> {
    await this.loadProfile();
  }

  async logout(): Promise<void> {
    await this.supabase.auth.signOut();
    this.session.set(null);
    this.profileUsername.set(null);
    this.linkedRiotAccount.set(null);
    this.profileLoading.set(false);
    this.clearLastSeen();
    this.activityCache.clear();
  }

  private sessionWithinTtl(session: Session | null, ignoreMissingLastSeen = false): boolean {
    if (!session) {
      return false;
    }
    const lastSeen = this.readLastSeen();
    if (ignoreMissingLastSeen && lastSeen == null) {
      return true;
    }
    return !isSessionExpired(lastSeen, Date.now(), SESSION_TTL_MS);
  }

  private async loadProfile(): Promise<void> {
    if (this.profileLoad) {
      return this.profileLoad;
    }
    this.profileLoad = this.doLoadProfile().finally(() => {
      this.profileLoad = null;
    });
    return this.profileLoad;
  }

  private async doLoadProfile(): Promise<void> {
    this.profileLoading.set(true);
    try {
      const accessToken = await this.resolveAccessToken();
      if (!accessToken) {
        this.profileUsername.set(null);
        this.linkedRiotAccount.set(null);
        return;
      }

      let me: AuthMeResponse;
      try {
        me = await firstValueFrom(
          this.http.get<AuthMeResponse>(apiUrl('/api/auth/me')).pipe(timeout(8000)),
        );
      } catch (error) {
        if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          await this.logout();
          return;
        }
        this.profileUsername.set(null);
        this.linkedRiotAccount.set(null);
        return;
      }

      this.profileUsername.set(me.username?.trim() || null);
      const previousLinkedId = this.linkedRiotAccount()?.id ?? null;
      const linked = me.linkedRiotAccount ?? (await this.fetchLinkedAccountFallback());
      const nextLinkedId = linked?.id ?? null;
      if (previousLinkedId !== nextLinkedId) {
        this.activityCache.invalidateActivity();
      }
      this.linkedRiotAccount.set(linked);
    } catch {
      this.profileUsername.set(null);
      this.linkedRiotAccount.set(null);
    } finally {
      this.profileLoading.set(false);
    }
  }

  private async fetchLinkedAccountFallback(): Promise<LinkedRiotAccount | null> {
    try {
      const accounts = await firstValueFrom(
        this.http.get<UserRiotAccount[]>(apiUrl('/api/me/riot-accounts')),
      );
      const account = accounts[0];
      if (!account) {
        return null;
      }
      return {
        id: account.id,
        gameName: account.gameName,
        tagLine: account.tagLine,
        riotId: account.riotId,
        profileIconId: account.profileIconId,
      };
    } catch {
      return null;
    }
  }

  private isAccessTokenExpired(accessToken: string, skewSeconds = 30): boolean {
    try {
      const payload = JSON.parse(atob(accessToken.split('.')[1])) as { exp?: number };
      if (typeof payload.exp !== 'number') {
        return true;
      }
      return Date.now() / 1000 >= payload.exp - skewSeconds;
    } catch {
      return true;
    }
  }

  private bindActivityTracking(): void {
    if (this.activityBound) {
      return;
    }
    this.activityBound = true;

    const onVisibilityChange = () => {
      if (document.visibilityState !== 'visible') {
        this.touchLastSeen();
        return;
      }
      // Zoneless CD schedules its render via rAF, which browsers pause while a tab is
      // backgrounded/unfocused. Confirmation/magic-link emails often open the callback
      // route in a tab that isn't focused yet, so state updates silently without a
      // repaint. Force a tick when the tab regains visibility so the UI catches up
      // without requiring a manual refresh.
      this.appRef.tick();
      if (this.session() && !this.sessionWithinTtl(this.session())) {
        void this.logout();
        return;
      }
      this.touchLastSeen();
    };
    const onPageHide = () => this.touchLastSeen();

    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('focus', () => this.appRef.tick());
    window.addEventListener('pagehide', onPageHide);
    window.setInterval(() => {
      if (document.visibilityState === 'visible' && this.session()) {
        this.touchLastSeen();
      }
    }, 5 * 60 * 1000);
  }

  private touchLastSeen(): void {
    try {
      localStorage.setItem(SESSION_LAST_SEEN_KEY, String(Date.now()));
    } catch {
      /* ignore */
    }
  }

  private readLastSeen(): number | null {
    try {
      return parseLastSeen(
        localStorage.getItem(SESSION_LAST_SEEN_KEY) ?? localStorage.getItem('riftrace.session.lastSeen'),
      );
    } catch {
      return null;
    }
  }

  private clearLastSeen(): void {
    try {
      localStorage.removeItem(SESSION_LAST_SEEN_KEY);
      localStorage.removeItem('riftrace.session.lastSeen');
    } catch {
      /* ignore */
    }
  }
}
