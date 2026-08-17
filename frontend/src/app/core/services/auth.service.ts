import { createClient, Session, SupabaseClient } from '@supabase/supabase-js';
import { Injectable, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly supabase: SupabaseClient = createClient(
    environment.supabaseUrl,
    environment.supabasePublishableKey,
    {
      auth: {
        detectSessionInUrl: true,
        flowType: 'pkce',
      },
    },
  );

  private readonly session = signal<Session | null>(null);
  private readonly initialized = signal(false);

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isInitialized = computed(() => this.initialized());
  readonly userEmail = computed(() => this.session()?.user.email ?? null);

  constructor() {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    const { data } = await this.supabase.auth.getSession();
    this.session.set(data.session);
    this.initialized.set(true);

    this.supabase.auth.onAuthStateChange((_event, session) => {
      this.session.set(session);
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
    const { data } = await this.supabase.auth.getSession();
    if (data.session?.access_token) {
      this.session.set(data.session);
      return data.session.access_token;
    }

    const refreshed = await this.supabase.auth.refreshSession();
    if (refreshed.error || !refreshed.data.session?.access_token) {
      this.session.set(null);
      return null;
    }

    this.session.set(refreshed.data.session);
    return refreshed.data.session.access_token;
  }

  async signInWithEmail(email: string, password: string): Promise<string | null> {
    const { data, error } = await this.supabase.auth.signInWithPassword({ email, password });
    if (!error) {
      this.session.set(data.session);
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
    const { error } = await this.supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    });
    return error?.message ?? null;
  }

  async logout(): Promise<void> {
    await this.supabase.auth.signOut();
    this.session.set(null);
  }
}
