import { Routes } from '@angular/router';
import { authHomeGuard, guestAuthGuard, guestHomeGuard } from './core/guards/home.guards';
import { AuthCallbackPageComponent } from './features/auth/auth-callback-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { SettingsPageComponent } from './features/settings/settings-page.component';
import { CreatedRacesPageComponent } from './features/home/created-races-page.component';
import { MyRacesPageComponent } from './features/home/my-races-page.component';
import { PublicRacesPageComponent } from './features/home/public-races-page.component';
import { CreateRacePageComponent } from './features/race/create-race-page.component';
import { RaceDetailPageComponent } from './features/race/race-detail-page.component';

export const routes: Routes = [
  {
    path: '',
    component: PublicRacesPageComponent,
    canActivate: [guestHomeGuard],
  },
  {
    path: 'login',
    component: LoginPageComponent,
    canActivate: [guestAuthGuard],
  },
  {
    path: 'auth/callback',
    component: AuthCallbackPageComponent,
  },
  {
    path: 'my-races',
    component: MyRacesPageComponent,
    canActivate: [authHomeGuard],
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
    component: CreateRacePageComponent,
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
