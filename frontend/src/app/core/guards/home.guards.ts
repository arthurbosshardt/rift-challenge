import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

export const guestHomeGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (auth.isAuthenticated()) {
    return router.createUrlTree(['/my-races']);
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

export const guestAuthGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (auth.isAuthenticated()) {
    return router.createUrlTree(['/my-races']);
  }
  return true;
};
