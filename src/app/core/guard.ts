import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth';
import { AdminApi } from './admin-api';

export const adminGuard: CanActivateFn = async () => {
  // Az inject() csak szinkron, az első await előtt hívható (utána már nincs injection context: NG0203).
  const auth = inject(AuthService);
  const api = inject(AdminApi);
  if (!(await auth.ensureLoggedIn())) {
    return false;
  }
  if (!api.me()) {
    await api.loadMe();
  }
  return true;
};
