import { Routes } from '@angular/router';
import {
  authHomeGuard,
  guestAuthGuard,
  linkedAccountGuard,
  myChallengesRedirectGuard,
  rootRedirectGuard,
} from './core/guards/home.guards';
import { RouteSeoData } from './core/seo/app-title-strategy';

export const routes: Routes = [
  {
    path: '',
    canActivate: [rootRedirectGuard],
    loadComponent: () =>
      import('./features/home/landing-page.component').then((m) => m.LandingPageComponent),
    data: {
      seo: {
        descriptionKey: 'seo.home.description',
        path: '/',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'home',
    loadComponent: () =>
      import('./features/home/landing-page.component').then((m) => m.LandingPageComponent),
    data: {
      seo: {
        descriptionKey: 'seo.home.description',
        path: '/home',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'concept',
    redirectTo: 'home',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login-redirect.component').then((m) => m.LoginRedirectComponent),
    canActivate: [guestAuthGuard],
    data: {
      seo: {
        titleKey: 'seo.login.title',
        descriptionKey: 'seo.login.description',
        path: '/login',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./features/auth/auth-callback-page.component').then((m) => m.AuthCallbackPageComponent),
    data: {
      seo: {
        titleKey: 'seo.auth.title',
        descriptionKey: 'seo.auth.description',
        path: '/auth/callback',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'auth/reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password-page.component').then((m) => m.ResetPasswordPageComponent),
    data: {
      seo: {
        titleKey: 'seo.auth.title',
        descriptionKey: 'seo.auth.description',
        path: '/auth/reset-password',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'dashboard',
    redirectTo: 'my-challenges',
  },
  {
    path: 'my-challenges',
    canActivate: [myChallengesRedirectGuard],
    children: [],
  },
  {
    path: 'my-participations',
    loadComponent: () =>
      import('./features/home/my-challenges-page.component').then((m) => m.MyChallengesPageComponent),
    canActivate: [linkedAccountGuard],
    data: {
      seo: {
        titleKey: 'seo.myParticipations.title',
        descriptionKey: 'seo.myParticipations.description',
        path: '/my-participations',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'my-races',
    redirectTo: 'my-participations',
  },
  {
    path: 'created-races',
    redirectTo: 'my-participations',
  },
  {
    path: 'created-challenges',
    redirectTo: 'my-participations',
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/settings-page.component').then((m) => m.SettingsPageComponent),
    canActivate: [authHomeGuard],
    data: {
      seo: {
        titleKey: 'seo.settings.title',
        descriptionKey: 'seo.settings.description',
        path: '/settings',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'challenges',
    loadComponent: () =>
      import('./features/home/public-challenges-page.component').then(
        (m) => m.PublicChallengesPageComponent,
      ),
    data: {
      seo: {
        titleKey: 'seo.publicChallenges.title',
        descriptionKey: 'seo.publicChallenges.description',
        path: '/challenges',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'public-challenges',
    redirectTo: 'challenges',
  },
  {
    path: 'public-races',
    redirectTo: 'challenges',
  },
  {
    path: 'challenges/new',
    loadComponent: () =>
      import('./features/challenge/create-challenge-redirect.component').then(
        (m) => m.CreateChallengeRedirectComponent,
      ),
    canActivate: [authHomeGuard],
    data: {
      seo: {
        titleKey: 'seo.createChallenge.title',
        descriptionKey: 'seo.createChallenge.description',
        path: '/challenges/new',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'races/new',
    redirectTo: 'challenges/new',
  },
  {
    path: 'challenges/:shareSlug',
    loadComponent: () =>
      import('./features/challenge/challenge-detail-page.component').then(
        (m) => m.ChallengeDetailPageComponent,
      ),
    data: {
      seo: {
        defer: true,
        path: '/challenges',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'races/:shareSlug',
    redirectTo: ({ params }) => `/challenges/${params['shareSlug']}`,
  },
  {
    path: 'players/:riotId',
    loadComponent: () =>
      import('./features/player/player-profile-page.component').then(
        (m) => m.PlayerProfilePageComponent,
      ),
    data: {
      seo: {
        defer: true,
        path: '/players',
      } satisfies RouteSeoData,
    },
  },
  {
    path: '**',
    redirectTo: '',
  },
];
