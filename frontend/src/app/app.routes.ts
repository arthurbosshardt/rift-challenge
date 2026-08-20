import { Routes } from '@angular/router';
import {
  authHomeGuard,
  guestAuthGuard,
  linkedAccountGuard,
  rootRedirectGuard,
} from './core/guards/home.guards';
import { AuthCallbackPageComponent } from './features/auth/auth-callback-page.component';
import { LoginRedirectComponent } from './features/auth/login-redirect.component';
import { SettingsPageComponent } from './features/settings/settings-page.component';
import { LandingPageComponent } from './features/home/landing-page.component';
import { MyActivityPageComponent } from './features/home/my-activity-page.component';
import { PublicChallengesPageComponent } from './features/home/public-challenges-page.component';
import { CreateChallengeRedirectComponent } from './features/challenge/create-challenge-redirect.component';
import { ChallengeDetailPageComponent } from './features/challenge/challenge-detail-page.component';
import { RouteSeoData } from './core/seo/app-title-strategy';

export const routes: Routes = [
  {
    path: '',
    canActivate: [rootRedirectGuard],
    component: LandingPageComponent,
    data: {
      seo: {
        titleKey: 'seo.home.title',
        descriptionKey: 'seo.home.description',
        path: '/',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'home',
    component: LandingPageComponent,
    data: {
      seo: {
        titleKey: 'seo.home.title',
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
    component: LoginRedirectComponent,
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
    component: AuthCallbackPageComponent,
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
    component: MyActivityPageComponent,
    canActivate: [linkedAccountGuard],
    data: {
      seo: {
        titleKey: 'seo.myChallenges.title',
        descriptionKey: 'seo.myChallenges.description',
        path: '/my-challenges',
        noindex: true,
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'my-races',
    redirectTo: 'my-challenges',
  },
  {
    path: 'created-races',
    redirectTo: 'my-challenges',
  },
  {
    path: 'created-challenges',
    redirectTo: 'my-challenges',
  },
  {
    path: 'settings',
    component: SettingsPageComponent,
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
    path: 'public-challenges',
    component: PublicChallengesPageComponent,
    data: {
      seo: {
        titleKey: 'seo.publicChallenges.title',
        descriptionKey: 'seo.publicChallenges.description',
        path: '/public-challenges',
      } satisfies RouteSeoData,
    },
  },
  {
    path: 'public-races',
    redirectTo: 'public-challenges',
  },
  {
    path: 'challenges/new',
    component: CreateChallengeRedirectComponent,
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
    component: ChallengeDetailPageComponent,
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
    path: '**',
    redirectTo: '',
  },
];
