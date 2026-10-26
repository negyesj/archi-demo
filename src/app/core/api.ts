import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TenantService } from './tenant';

type Params = Record<string, string | number | boolean | null | undefined>;

/** Tenant-szintű API hívások: /api/t/{tenant}/{szolgáltatás}/... */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly tenant = inject(TenantService);

  private url(path: string): string {
    return `/api/t/${this.tenant.code()}/${path.replace(/^\//, '')}`;
  }

  private params(p?: Params): HttpParams {
    let hp = new HttpParams();
    Object.entries(p ?? {}).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') {
        hp = hp.set(k, String(v));
      }
    });
    return hp;
  }

  get<T>(path: string, params?: Params): Observable<T> {
    return this.http.get<T>(this.url(path), { params: this.params(params) });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(this.url(path), body);
  }

  put<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.put<T>(this.url(path), body);
  }

  delete<T>(path: string, params?: Params): Observable<T> {
    return this.http.delete<T>(this.url(path), { params: this.params(params) });
  }
}
