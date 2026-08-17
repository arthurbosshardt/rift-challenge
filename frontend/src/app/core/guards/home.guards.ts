import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

async function defaultAuthenticatedRoute(auth: AuthService): Promise<string[]> {
  if (!auth.linkedAccount()) {
    await auth.refreshProfile();
  }
  return auth.linkedAccount() ? ['/my-races'] : ['/public-races'];
}

export const guestHomeGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (auth.isAuthenticated()) {
    return router.createUrlTree(await defaultAuthenticatedRoute(auth));
  }
  return true;
};

export const authHomeGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  return true;
};

export const linkedAccountGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (!auth.linkedAccount()) {
    await auth.refreshProfile();
  }

  if (!auth.linkedAccount()) {
    return router.createUrlTree(['/public-races']);
  }

  return true;
};

export const guestAuthGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (auth.isAuthenticated()) {
    return router.createUrlTree(await defaultAuthenticatedRoute(auth));
  }
  return true;
};
