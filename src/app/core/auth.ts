import { Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { ConfigService } from './config';

/** Bejelentkezés a Keycloak {@code platform} realmjébe (admin-ui kliens, code flow + PKCE). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly config = inject(ConfigService);
  private configured = false;
  readonly userName = signal('');

  async ensureLoggedIn(): Promise<boolean> {
    if (!this.configured) {
      this.oauth.configure({
        issuer: `${this.config.value.keycloakUrl}/realms/platform`,
        clientId: 'admin-ui',
        responseType: 'code',
        redirectUri: `${window.location.origin}/`,
        postLogoutRedirectUri: `${window.location.origin}/`,
        scope: 'openid profile email',
        requireHttps: 'remoteOnly',
      });
      this.configured = true;
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.oauth.setupAutomaticSilentRefresh();
    }
    if (this.oauth.hasValidAccessToken()) {
      const c = this.oauth.getIdentityClaims() as { name?: string; preferred_username?: string };
      this.userName.set(c?.name ?? c?.preferred_username ?? '');
      return true;
    }
    this.oauth.initCodeFlow();
    return false;
  }

  token(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }

  logout(): void {
    this.oauth.logOut();
  }
}
