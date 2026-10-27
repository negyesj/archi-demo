import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { confirm, SecretDialog } from '../../shared/confirm';

interface ApiClient { id: string; clientId: string; name: string; userId: string; active: boolean; createdAt: string; }
interface Credentials { client: ApiClient; clientSecret: string; tokenUrl: string; }
interface Subscription { id: string; name: string; url: string; eventTypes: string[]; active: boolean; suspended: boolean; consecutiveFailures: number; }
interface Delivery { id: string; subscriptionId: string; eventType: string; status: string; attempts: number; lastStatus: number | null; lastError: string | null; createdAt: string; }

/**
 * Integrációk: API kliensek a publikus API-hoz (OAuth2 client credentials) és webhook feliratkozások.
 * Az API kliens jogosultságait a Jogosultságok oldalon, felhasználóként lehet kiosztani.
 */
@Component({
  selector: 'app-integrations',
  imports: [DatePipe, FormsModule, MatTabsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <h1>Integrációk</h1>
    <mat-tab-group>
      <mat-tab label="API kliensek">
        <div class="filters">
          <div class="form-item"><label class="form-label">Megnevezés</label><mat-form-field><input matInput [(ngModel)]="clientName" /></mat-form-field></div>
          <div class="form-item"><label class="form-label">Kliens azonosító</label><mat-form-field><input matInput [(ngModel)]="clientId" placeholder="pl. erp-rendszer" /><mat-hint>api-&lt;azonosító&gt; néven jön létre</mat-hint></mat-form-field></div>
          <button mat-flat-button [disabled]="!clientName || !clientId" (click)="createClient()"><mat-icon>add</mat-icon> Létrehozás</button>
        </div>
        <table mat-table [dataSource]="clients()" class="full">
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Megnevezés</th><td mat-cell *matCellDef="let c">{{ c.name }}</td></ng-container>
          <ng-container matColumnDef="clientId"><th mat-header-cell *matHeaderCellDef>client_id</th><td mat-cell *matCellDef="let c"><code>{{ c.clientId }}</code></td></ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Állapot</th><td mat-cell *matCellDef="let c">{{ c.active ? 'aktív' : 'visszavonva' }}</td></ng-container>
          <ng-container matColumnDef="created"><th mat-header-cell *matHeaderCellDef>Létrehozva</th><td mat-cell *matCellDef="let c">{{ c.createdAt | date: 'yyyy.MM.dd HH:mm' }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let c" class="actions">
              @if (c.active) {
                <button mat-icon-button matTooltip="Új titok" (click)="rotate(c)"><mat-icon>autorenew</mat-icon></button>
                <button mat-icon-button matTooltip="Visszavonás" (click)="revoke(c)"><mat-icon>block</mat-icon></button>
              }
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="clientCols"></tr>
          <tr mat-row *matRowDef="let row; columns: clientCols"></tr>
        </table>
      </mat-tab>
      <mat-tab label="Webhookok">
        <div class="filters">
          <div class="form-item"><label class="form-label">Megnevezés</label><mat-form-field><input matInput [(ngModel)]="hookName" /></mat-form-field></div>
          <div class="form-item wide"><label class="form-label">Cél URL (https)</label><mat-form-field><input matInput [(ngModel)]="hookUrl" /></mat-form-field></div>
          <div class="form-item wide"><label class="form-label">Eseménytípusok (vesszővel)</label><mat-form-field><input matInput [(ngModel)]="hookTypes" placeholder="irat.kuldemeny.iktatas, partner.*" /></mat-form-field></div>
          <button mat-flat-button [disabled]="!hookName || !hookUrl || !hookTypes" (click)="createHook()"><mat-icon>add</mat-icon> Feliratkozás</button>
        </div>
        <table mat-table [dataSource]="subs()" class="full">
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Megnevezés</th><td mat-cell *matCellDef="let s">{{ s.name }}</td></ng-container>
          <ng-container matColumnDef="url"><th mat-header-cell *matHeaderCellDef>URL</th><td mat-cell *matCellDef="let s"><code>{{ s.url }}</code></td></ng-container>
          <ng-container matColumnDef="types"><th mat-header-cell *matHeaderCellDef>Események</th><td mat-cell *matCellDef="let s">{{ s.eventTypes.join(', ') }}</td></ng-container>
          <ng-container matColumnDef="state"><th mat-header-cell *matHeaderCellDef>Állapot</th>
            <td mat-cell *matCellDef="let s">{{ s.suspended ? 'felfüggesztve (' + s.consecutiveFailures + ' hiba)' : (s.active ? 'aktív' : 'inaktív') }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let s" class="actions">
              <button mat-icon-button matTooltip="Kézbesítések" (click)="loadDeliveries(s)"><mat-icon>list</mat-icon></button>
              <button mat-icon-button matTooltip="Törlés" (click)="deleteHook(s)"><mat-icon>delete</mat-icon></button>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="subCols"></tr>
          <tr mat-row *matRowDef="let row; columns: subCols"></tr>
        </table>
        @if (deliveries().length) {
          <h3>Kézbesítések</h3>
          <table mat-table [dataSource]="deliveries()" class="full">
            <ng-container matColumnDef="created"><th mat-header-cell *matHeaderCellDef>Idő</th><td mat-cell *matCellDef="let d">{{ d.createdAt | date: 'yyyy.MM.dd HH:mm:ss' }}</td></ng-container>
            <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Esemény</th><td mat-cell *matCellDef="let d">{{ d.eventType }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Állapot</th><td mat-cell *matCellDef="let d">{{ d.status }} ({{ d.attempts }}×) {{ d.lastError ?? '' }}</td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let d" class="actions">
                @if (d.status !== 'SUCCEEDED') { <button mat-icon-button matTooltip="Újraküldés" (click)="retry(d)"><mat-icon>replay</mat-icon></button> }
              </td></ng-container>
            <tr mat-header-row *matHeaderRowDef="delCols"></tr>
            <tr mat-row *matRowDef="let row; columns: delCols"></tr>
          </table>
        }
      </mat-tab>
    </mat-tab-group>
  `,
})
export class Integrations {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly clientCols = ['name', 'clientId', 'active', 'created', 'actions'];
  readonly subCols = ['name', 'url', 'types', 'state', 'actions'];
  readonly delCols = ['created', 'type', 'status', 'actions'];
  readonly clients = signal<ApiClient[]>([]);
  readonly subs = signal<Subscription[]>([]);
  readonly deliveries = signal<Delivery[]>([]);
  private selectedSub: Subscription | null = null;
  clientName = '';
  clientId = '';
  hookName = '';
  hookUrl = '';
  hookTypes = '';

  constructor() {
    this.loadClients();
    this.loadSubs();
  }

  loadClients(): void {
    this.api.get<ApiClient[]>('iam/api-clients').subscribe((c) => this.clients.set(c));
  }

  loadSubs(): void {
    this.api.get<Subscription[]>('webhook/subscriptions').subscribe((s) => this.subs.set(s));
  }

  private showCredentials(c: Credentials): void {
    this.dialog.open(SecretDialog, {
      data: {
        title: 'API kliens hitelesítő adatai',
        message: `client_id: ${c.client.clientId} – token végpont: ${c.tokenUrl} (grant_type=client_credentials). client_secret:`,
        secret: c.clientSecret,
      },
      width: '640px',
    });
  }

  createClient(): void {
    this.api.post<Credentials>('iam/api-clients', { name: this.clientName, clientId: this.clientId }).subscribe((c) => {
      this.clientName = '';
      this.clientId = '';
      this.showCredentials(c);
      this.loadClients();
    });
  }

  rotate(c: ApiClient): void {
    this.api.post<Credentials>(`iam/api-clients/${c.id}/rotate-secret`).subscribe((cr) => this.showCredentials(cr));
  }

  revoke(c: ApiClient): void {
    confirm(this.dialog, { title: 'API kliens visszavonása', message: `${c.name}: a kliens többé nem kérhet tokent.`, ok: 'Visszavonás' })
      .subscribe((ok) => ok && this.api.delete(`iam/api-clients/${c.id}`).subscribe(() => this.loadClients()));
  }

  createHook(): void {
    const eventTypes = this.hookTypes.split(',').map((s) => s.trim()).filter(Boolean);
    this.api.post<{ subscription: Subscription; signingSecret: string }>('webhook/subscriptions', { name: this.hookName, url: this.hookUrl, eventTypes })
      .subscribe((r) => {
        this.dialog.open(SecretDialog, { data: { title: 'Webhook aláíró titok', message: 'X-Webhook-Signature = sha256=HMAC(titok, timestamp + "." + body)', secret: r.signingSecret } });
        this.hookName = '';
        this.hookUrl = '';
        this.hookTypes = '';
        this.loadSubs();
      });
  }

  deleteHook(s: Subscription): void {
    confirm(this.dialog, { title: 'Feliratkozás törlése', message: s.name, ok: 'Törlés' })
      .subscribe((ok) => ok && this.api.delete(`webhook/subscriptions/${s.id}`).subscribe(() => { this.deliveries.set([]); this.loadSubs(); }));
  }

  loadDeliveries(s: Subscription): void {
    this.selectedSub = s;
    this.api.get<Delivery[]>('webhook/deliveries', { subscriptionId: s.id }).subscribe((d) => this.deliveries.set(d));
  }

  retry(d: Delivery): void {
    this.api.post(`webhook/deliveries/${d.id}/retry`).subscribe(() => {
      this.notify.ok('Újraküldés ütemezve');
      if (this.selectedSub) {
        this.loadDeliveries(this.selectedSub);
      }
    });
  }
}
