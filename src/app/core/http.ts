import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth';
import { NotifyService } from './notify';

/** Bearer token a saját API hívásokhoz. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  if (token && req.url.startsWith('/api/admin/')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/** Egységes hibaüzenet: a Problem Details címe, részletei és a hibakód (traceId) a supportnak. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notify = inject(NotifyService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const body = (err.error ?? {}) as { title?: string; detail?: string; traceId?: string; errors?: Record<string, string> };
      const traceId = body.traceId ?? err.headers?.get('X-Trace-Id') ?? '';
      const fields = body.errors ? ' ' + Object.entries(body.errors).map(([k, v]) => `${k}: ${v}`).join('; ') : '';
      const text = `${body.title ?? 'Hiba'}: ${body.detail ?? err.message}${fields}`;
      if (err.status !== 401) {
        notify.error(traceId ? `${text} (hibakód: ${traceId})` : text);
      }
      return throwError(() => err);
    }),
  );
};
