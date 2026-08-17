import { Routes } from '@angular/router';
import {
  authHomeGuard,
  guestAuthGuard,
  guestHomeGuard,
  linkedAccountGuard,
} from './core/guards/home.guards';
import { AuthCallbackPageComponent } from './features/auth/auth-callback-page.component';
import { LoginRedirectComponent } from './features/auth/login-redirect.component';
import { SettingsPageComponent } from './features/settings/settings-page.component';
import { CreatedRacesPageComponent } from './features/home/created-races-page.component';
import { MyRacesPageComponent } from './features/home/my-races-page.component';
import { PublicRacesPageComponent } from './features/home/public-races-page.component';
import { CreateRaceRedirectComponent } from './features/race/create-race-redirect.component';
import { RaceDetailPageComponent } from './features/race/race-detail-page.component';

export const routes: Routes = [
  {
    path: '',
    component: PublicRacesPageComponent,
    canActivate: [guestHomeGuard],
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
    path: 'my-races',
    component: MyRacesPageComponent,
    canActivate: [linkedAccountGuard],
  },
  {
    path: 'created-races',
    component: CreatedRacesPageComponent,
    canActivate: [authHomeGuard],
  },
  {
    path: 'settings',
    component: SettingsPageComponent,
    canActivate: [authHomeGuard],
  },
  {
    path: 'public-races',
    component: PublicRacesPageComponent,
    canActivate: [authHomeGuard],
  },
  {
    path: 'races/new',
    component: CreateRaceRedirectComponent,
    canActivate: [authHomeGuard],
  },
  {
    path: 'races/:shareSlug',
    component: RaceDetailPageComponent,
  },
  {
    path: '**',
    redirectTo: '',
  },
];
