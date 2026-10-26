import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';

type Params = Record<string, string | number | boolean | null | undefined>;

export interface AdminUser {
  id: string; username: string; email: string; fullName: string; role: 'PLATFORM_ADMIN' | 'SUPPORT'; active: boolean;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  readonly me = signal<AdminUser | null>(null);

  async loadMe(): Promise<void> {
    this.me.set(await firstValueFrom(this.get<AdminUser>('me')));
  }

  isAdmin(): boolean {
    return this.me()?.role === 'PLATFORM_ADMIN';
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
    return this.http.get<T>(`/api/admin/${path}`, { params: this.params(params) });
  }

  post<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.post<T>(`/api/admin/${path}`, body);
  }

  put<T>(path: string, body: unknown = {}): Observable<T> {
    return this.http.put<T>(`/api/admin/${path}`, body);
  }

  delete<T>(path: string, params?: Params): Observable<T> {
    return this.http.delete<T>(`/api/admin/${path}`, { params: this.params(params) });
  }
}
