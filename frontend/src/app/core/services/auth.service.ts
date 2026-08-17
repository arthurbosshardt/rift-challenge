import { Injectable, computed, signal } from '@angular/core';
import { createClient, Session, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../../environments/environment';
import { AuthMeResponse } from '../models/race.models';
import { apiUrl } from '../utils/api-url';
import {
  SESSION_LAST_SEEN_KEY,
  SESSION_TTL_MS,
  isSessionExpired,
  parseLastSeen,
} from '../auth/session-ttl';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly supabase: SupabaseClient = createClient(
    environment.supabaseUrl,
    environment.supabasePublishableKey,
    {
      auth: {
        detectSessionInUrl: true,
        flowType: 'pkce',
        persistSession: true,
        autoRefreshToken: true,
      },
    },
  );

  private readonly session = signal<Session | null>(null);
  private readonly initialized = signal(false);
  private readonly profileUsername = signal<string | null>(null);
  private readonly profileLoading = signal(false);
  private visibilityHandler?: () => void;

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isInitialized = computed(() => this.initialized());
  readonly userEmail = computed(() => this.session()?.user.email ?? null);
  readonly displayName = computed(() => this.profileUsername());
  readonly isProfileLoading = computed(() => this.profileLoading());

  constructor() {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    const { data } = await this.supabase.auth.getSession();
    const validSession = this.sessionWithinTtl(data.session) ? data.session : null;

    if (data.session && !validSession) {
      await this.supabase.auth.signOut();
      this.clearLastSeen();
    }

    this.session.set(validSession);
    if (validSession) {
      this.touchLastSeen();
      void this.loadProfile(validSession.access_token);
    }
    this.initialized.set(true);
    this.bindActivityTracking();

    this.supabase.auth.onAuthStateChange((_event, session) => {
      if (session && !this.sessionWithinTtl(session, true)) {
        void this.logout();
        return;
      }
      this.session.set(session);
      if (session) {
        this.touchLastSeen();
        void this.loadProfile(session.access_token);
      } else {
        this.profileUsername.set(null);
        this.profileLoading.set(false);
        this.clearLastSeen();
      }
    });
  }

  async waitUntilReady(): Promise<void> {
    while (!this.initialized()) {
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
  }

  async completeOAuthOrEmailCallback(): Promise<string | null> {
    await this.waitUntilReady();

    const callbackUrl = window.location.href;
    const hasOAuthCode =
      new URL(callbackUrl).searchParams.has('code') || callbackUrl.includes('access_token=');

    if (hasOAuthCode) {
      const { data, error } = await this.supabase.auth.exchangeCodeForSession(callbackUrl);
      if (error) {
        this.session.set(null);
        return error.message;
      }
      this.session.set(data.session);
      this.touchLastSeen();
      if (data.session) {
        void this.loadProfile(data.session.access_token);
      }
      return null;
    }

    const { data } = await this.supabase.auth.getSession();
    this.session.set(data.session);
    return null;
  }

  getAccessToken(): string | null {
    return this.session()?.access_token ?? null;
  }

  async resolveAccessToken(): Promise<string | null> {
    if (this.session() && !this.sessionWithinTtl(this.session())) {
      await this.logout();
      return null;
    }

    const { data } = await this.supabase.auth.getSession();
    if (data.session?.access_token) {
      if (!this.sessionWithinTtl(data.session)) {
        await this.logout();
        return null;
      }
      this.session.set(data.session);
      this.touchLastSeen();
      return data.session.access_token;
    }

    const refreshed = await this.supabase.auth.refreshSession();
    if (refreshed.error || !refreshed.data.session?.access_token) {
      this.session.set(null);
      return null;
    }

    this.session.set(refreshed.data.session);
    this.touchLastSeen();
    return refreshed.data.session.access_token;
  }

  async signInWithEmail(email: string, password: string): Promise<string | null> {
    const { data, error } = await this.supabase.auth.signInWithPassword({ email, password });
    if (!error) {
      this.session.set(data.session);
      this.touchLastSeen();
      if (data.session) {
        void this.loadProfile(data.session.access_token);
      }
    }
    return error?.message ?? null;
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
    return error?.message ?? null;
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
      return error.message;
    }
    if (!data.url) {
      return 'auth.googleStartError';
    }

    const oauthUrl = new URL(data.url);
    oauthUrl.searchParams.set('redirect_to', redirectTo);
    window.location.assign(oauthUrl.toString());
    return null;
  }

  async logout(): Promise<void> {
    await this.supabase.auth.signOut();
    this.session.set(null);
    this.profileUsername.set(null);
    this.profileLoading.set(false);
    this.clearLastSeen();
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

  private async loadProfile(accessToken: string): Promise<void> {
    this.profileLoading.set(true);
    try {
      const response = await fetch(apiUrl('/api/auth/me'), {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!response.ok) {
        this.profileUsername.set(null);
        return;
      }
      const me = (await response.json()) as AuthMeResponse;
      this.profileUsername.set(me.username?.trim() || null);
    } catch {
      this.profileUsername.set(null);
    } finally {
      this.profileLoading.set(false);
    }
  }

  private bindActivityTracking(): void {
    this.visibilityHandler = () => {
      if (document.visibilityState !== 'visible') {
        this.touchLastSeen();
        return;
      }
      if (this.session() && !this.sessionWithinTtl(this.session())) {
        void this.logout();
        return;
      }
      this.touchLastSeen();
    };
    document.addEventListener('visibilitychange', this.visibilityHandler);
    window.addEventListener('pagehide', () => this.touchLastSeen());
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
      return parseLastSeen(localStorage.getItem(SESSION_LAST_SEEN_KEY));
    } catch {
      return null;
    }
  }

  private clearLastSeen(): void {
    try {
      localStorage.removeItem(SESSION_LAST_SEEN_KEY);
    } catch {
      /* ignore */
    }
  }
}
