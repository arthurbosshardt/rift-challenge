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
import { MyChallengesPageComponent } from './features/home/my-challenges-page.component';
import { DashboardPageComponent } from './features/home/dashboard-page.component';
import { PublicChallengesPageComponent } from './features/home/public-challenges-page.component';
import { CreateChallengeRedirectComponent } from './features/challenge/create-challenge-redirect.component';
import { ChallengeDetailPageComponent } from './features/challenge/challenge-detail-page.component';

export const routes: Routes = [
  {
    path: '',
    canActivate: [rootRedirectGuard],
    component: LandingPageComponent,
  },
  {
    path: 'home',
    component: LandingPageComponent,
  },
  {
    path: 'concept',
    redirectTo: 'home',
  },
  {
    path: 'login',
    component: LoginRedirectComponent,
    canActivate: [guestAuthGuard],
  },
  {
    path: 'auth/callback',
    component: AuthCallbackPageComponent,
  },
  {
    path: 'auth/reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password-page.component').then((m) => m.ResetPasswordPageComponent),
  },
  {
    path: 'dashboard',
    component: DashboardPageComponent,
    canActivate: [linkedAccountGuard],
  },
  {
    path: 'my-challenges',
    component: MyChallengesPageComponent,
    canActivate: [linkedAccountGuard],
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
  },
  {
    path: 'public-challenges',
    component: PublicChallengesPageComponent,
  },
  {
    path: 'public-races',
    redirectTo: 'public-challenges',
  },
  {
    path: 'challenges/new',
    component: CreateChallengeRedirectComponent,
    canActivate: [authHomeGuard],
  },
  {
    path: 'races/new',
    redirectTo: 'challenges/new',
  },
  {
    path: 'challenges/:shareSlug',
    component: ChallengeDetailPageComponent,
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
