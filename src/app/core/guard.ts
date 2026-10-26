import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth';
import { AdminApi } from './admin-api';

export const adminGuard: CanActivateFn = async () => {
  if (!(await inject(AuthService).ensureLoggedIn())) {
    return false;
  }
  const api = inject(AdminApi);
  if (!api.me()) {
    await api.loadMe();
  }
  return true;
};
