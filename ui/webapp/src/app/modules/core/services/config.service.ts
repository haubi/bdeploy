import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, Observable } from 'rxjs';
import { catchError, delay, retryWhen, tap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import {
  BackendInfoDto,
  MinionMode,
  PluginInfoDto,
  Version,
} from '../../../models/gen.dtos';
import { ConnectionLostComponent } from '../components/connection-lost/connection-lost.component';
import {
  ConnectionVersionComponent,
  VERSION_DATA,
} from '../components/connection-version/connection-version.component';
import { NO_LOADING_BAR_CONTEXT } from '../utils/loading-bar.util';
import { suppressGlobalErrorHandling } from '../utils/server.utils';
import { ThemeService } from './theme.service';

export interface AppConfig {
  version: Version;
  hostname: string;
  api: string;
  ws: string;
  mode: MinionMode;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  public config: AppConfig;

  private checkInterval;
  private isUnreachable = false;
  private overlayRef: OverlayRef;
  private versionLock = false;

  private backendTimeOffset = 0;
  private backendOffsetWarning = false;

  isCentral$ = new BehaviorSubject<boolean>(false);
  isManaged$ = new BehaviorSubject<boolean>(false);
  isStandalone$ = new BehaviorSubject<boolean>(false);

  // prettier-ignore
  constructor(
    private themes: ThemeService /* dummy: required to bootstrap theming early! */,
    private http: HttpClient,
    private overlay: Overlay,
    iconRegistry: MatIconRegistry,
    sanitizer: DomSanitizer
  ) {
    // register all custom icons we want to use with <mat-icon>
    iconRegistry.addSvgIcon('bdeploy', sanitizer.bypassSecurityTrustResourceUrl('assets/logo-single-path-square.svg'));
    iconRegistry.addSvgIcon('progress', sanitizer.bypassSecurityTrustResourceUrl('assets/progress.svg'));
    iconRegistry.addSvgIcon('plus', sanitizer.bypassSecurityTrustResourceUrl('assets/plus.svg'));
    iconRegistry.addSvgIcon('star', sanitizer.bypassSecurityTrustResourceUrl('assets/star.svg'));
    iconRegistry.addSvgIcon('group-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/group-settings.svg'));
    iconRegistry.addSvgIcon('instance-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/instance-settings.svg'));
    iconRegistry.addSvgIcon('repository-settings', sanitizer.bypassSecurityTrustResourceUrl('assets/repository-settings.svg'));
    iconRegistry.addSvgIcon('start-scheduled', sanitizer.bypassSecurityTrustResourceUrl('assets/start_schedule.svg'));
    iconRegistry.addSvgIcon('stop-scheduled', sanitizer.bypassSecurityTrustResourceUrl('assets/stop_schedule.svg'));
    iconRegistry.addSvgIcon('sync-all', sanitizer.bypassSecurityTrustResourceUrl('assets/syncall.svg'));

    iconRegistry.addSvgIcon('LINUX', sanitizer.bypassSecurityTrustResourceUrl('assets/linux.svg'));
    iconRegistry.addSvgIcon('WINDOWS', sanitizer.bypassSecurityTrustResourceUrl('assets/windows.svg'));
    iconRegistry.addSvgIcon('AIX', sanitizer.bypassSecurityTrustResourceUrl('assets/aix.svg'));
    iconRegistry.addSvgIcon('MACOS', sanitizer.bypassSecurityTrustResourceUrl('assets/mac.svg'));
    iconRegistry.addSvgIcon('WEB', sanitizer.bypassSecurityTrustResourceUrl('assets/web.svg'));
    iconRegistry.addSvgIcon('global_pin', sanitizer.bypassSecurityTrustResourceUrl('assets/global-pin.svg'));

    // check whether the server version changed every minute.
    // *usually* we loose the server connection for a short period when this happens, so the interval is just a fallback.
    this.checkInterval = setInterval(() => this.checkServerVersion(), 60000);
  }

  /** Used during application init to load the configuration. */
  public load(): Promise<AppConfig> {
    return new Promise((resolve) => {
      this.getBackendInfo(true).subscribe({
        next: (bv) => {
          this.config = {
            version: bv.version,
            hostname: bv.name,
            api: environment.apiUrl,
            ws: environment.wsUrl,
            mode: bv.mode,
          };
          console.log('API URL set to ' + this.config.api);
          console.log('WS URL set to ' + this.config.ws);
          console.log('Remote reports mode ' + this.config.mode);
          this.isCentral$.next(this.config.mode === MinionMode.CENTRAL);
          this.isManaged$.next(this.config.mode === MinionMode.MANAGED);
          this.isStandalone$.next(this.config.mode === MinionMode.STANDALONE);
          resolve(this.config);
        },
        error: (err) => {
          console.error('Cannot load configuration', err);
        },
      });
    });
  }

  /** Check whether there is a new version running on the backend, show dialog if it is. */
  public checkServerVersion() {
    this.getBackendInfo(true).subscribe((bv) => {
      this.doCheckVersion(bv);
    });
  }

  private doCheckVersion(bv: BackendInfoDto) {
    if (!isEqual(this.config.version, bv.version)) {
      if (this.overlayRef) {
        if (this.isUnreachable) {
          // we were recovering and now the backend reports another version.
          this.closeOverlay();
        } else {
          return; // we're already showing the new version overlay.
        }
      }

      // there is no return from here anyway. The user must reload the application.
      this.stopCheckAndLockVersion();

      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay
          .position()
          .global()
          .centerHorizontally()
          .centerVertically(),
        hasBackdrop: true,
      });

      // create a portal with a custom injector which passes the received version data to show it.
      const portal = new ComponentPortal(
        ConnectionVersionComponent,
        null,
        Injector.create({
          providers: [
            {
              provide: VERSION_DATA,
              useValue: {
                oldVersion: this.config.version,
                newVersion: bv.version,
              },
            },
          ],
        })
      );
      this.overlayRef.attach(portal);
    }
  }

  /** Stops the server version check. This should be used if we *expect* (and handle) a changing server version, e.g. update. */
  private stopCheckAndLockVersion() {
    clearInterval(this.checkInterval);
    this.versionLock = true;
  }

  /** Call in case of suspected problems with the backend connection, will show a dialog until server connection is restored. */
  public checkServerReachable(): void {
    if (!this.isUnreachable) {
      this.isUnreachable = true;

      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay
          .position()
          .global()
          .centerHorizontally()
          .centerVertically(),
        hasBackdrop: true,
      });

      const portal = new ComponentPortal(ConnectionLostComponent);
      this.overlayRef.attach(portal);

      this.getBackendInfo()
        .pipe(retryWhen((errors) => errors.pipe(delay(2000))))
        .subscribe((r) => {
          if (this.versionLock) {
            return;
          }

          if (!this.config) {
            window.location.reload();
          } else {
            this.doCheckVersion(r);
          }

          this.closeOverlay();
          this.isUnreachable = false;
        });
    }
  }

  /** Closes the overlay (connection lost, server version) if present */
  private closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

  /** The base URL for a given plugin */
  public getPluginUrl(plugin: PluginInfoDto) {
    return this.config.api + '/plugins/' + plugin.id.id;
  }

  /** Tries to fetch the current server version, suppresses global error handling */
  public getBackendInfo(errorHandling = false): Observable<BackendInfoDto> {
    return this.http
      .get<BackendInfoDto>(environment.apiUrl + '/backend-info/version', {
        headers: errorHandling
          ? {}
          : suppressGlobalErrorHandling(new HttpHeaders()),
        context: NO_LOADING_BAR_CONTEXT,
      })
      .pipe(
        catchError((e) => {
          if (e?.status === 404) {
            // in case the version backend is no longer available, it is extremely likely
            // that the server has been migrated to NODE (nodes don't have UI backends).
            window.location.reload();
          }
          throw e;
        }),
        tap((v) => {
          const serverTime = v.time;
          const clientTime = Date.now();

          // calculate the time offset between the client and the server
          this.backendTimeOffset = serverTime - clientTime;

          // log if we're exceeding a certain threshold
          if (this.backendTimeOffset > 500 && !this.backendOffsetWarning) {
            console.warn('Server time offset', this.backendTimeOffset, 'ms');
            this.backendOffsetWarning = true;
          }
        })
      );
  }

  public getCorrectedNow(): number {
    return Date.now() + this.backendTimeOffset;
  }

  public isCurrentlyUnreachable(): boolean {
    return this.isUnreachable;
  }
}
