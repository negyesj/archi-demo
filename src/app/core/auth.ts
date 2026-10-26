import { Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { ConfigService } from './config';

/**
 * Bejelentkezés a tenant saját Keycloak realmjébe (OIDC code flow + PKCE). A Keycloak csak hitelesít;
 * a jogosultságokat az IAM szolgáltatás adja (lásd UserContextService).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly config = inject(ConfigService);
  private realm: string | null = null;
  readonly userName = signal<string>('');

  /** Igaz, ha van érvényes token a tenanthoz; különben elindítja a bejelentkezést. */
  async ensureLoggedIn(tenant: string): Promise<boolean> {
    if (this.realm !== tenant) {
      if (this.realm !== null) {
        this.oauth.logOut(true);
      }
      this.oauth.configure({
        issuer: `${this.config.value.keycloakUrl}/realms/${tenant}`,
        clientId: 'app',
        responseType: 'code',
        redirectUri: `${window.location.origin}/t/${tenant}`,
        postLogoutRedirectUri: `${window.location.origin}/t/${tenant}`,
        scope: 'openid profile email',
        requireHttps: 'remoteOnly',
        showDebugInformation: false,
      });
      this.realm = tenant;
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.oauth.setupAutomaticSilentRefresh();
    }
    if (this.oauth.hasValidAccessToken()) {
      const claims = this.oauth.getIdentityClaims() as { name?: string; preferred_username?: string };
      this.userName.set(claims?.name ?? claims?.preferred_username ?? '');
      return true;
    }
    this.oauth.initCodeFlow(window.location.pathname);
    return false;
  }

  token(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }

  logout(): void {
    this.oauth.logOut();
  }
}
