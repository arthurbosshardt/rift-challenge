import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthModalService } from '../../core/services/auth-modal.service';

async function defaultAuthenticatedRoute(auth: AuthService): Promise<string[]> {
  if (!auth.linkedAccount()) {
    await auth.refreshProfile();
  }
  const linkedAccount = auth.linkedAccount();
  return linkedAccount ? ['/players', linkedAccount.riotId] : ['/challenges'];
}

export const rootRedirectGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (auth.isAuthenticated()) {
    return router.createUrlTree(await defaultAuthenticatedRoute(auth));
  }
  return router.createUrlTree(['/home']);
};

export const authHomeGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const authModal = inject(AuthModalService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (!auth.isAuthenticated()) {
    authModal.open({ returnUrl: state.url });
    return router.createUrlTree(['/home']);
  }
  return true;
};

export const linkedAccountGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const authModal = inject(AuthModalService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (!auth.isAuthenticated()) {
    authModal.open({ returnUrl: state.url });
    return router.createUrlTree(['/home']);
  }

  if (!auth.linkedAccount()) {
    await auth.refreshProfile();
  }

  if (!auth.linkedAccount()) {
    return router.createUrlTree(['/challenges']);
  }

  return true;
};

/** `/my-challenges` is a legacy URL kept for old bookmarks/links; it always redirects to the owner's `/players/:riotId`. */
export const myChallengesRedirectGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const authModal = inject(AuthModalService);
  const router = inject(Router);
  await auth.waitUntilReady();

  if (!auth.isAuthenticated()) {
    authModal.open({ returnUrl: state.url });
    return router.createUrlTree(['/home']);
  }

  if (!auth.linkedAccount()) {
    await auth.refreshProfile();
  }

  const linkedAccount = auth.linkedAccount();
  if (!linkedAccount) {
    return router.createUrlTree(['/challenges']);
  }

  return router.createUrlTree(['/players', linkedAccount.riotId]);
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
