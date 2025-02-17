import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { combineLatest, Observable, of, ReplaySubject } from 'rxjs';
import { mergeMap, share } from 'rxjs/operators';
import {
  InstanceDto,
  InstanceGroupConfiguration,
  InstanceStateRecord,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstanceStateService {
  public state$: Observable<InstanceStateRecord>;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private instances: InstancesService
  ) {
    this.state$ = combineLatest([
      this.instances.current$,
      this.groups.current$,
      this.instances.instances$, // trigger to reload on changes.
    ]).pipe(
      mergeMap(([i, g]) => this.getLoadCall(i, g)),
      share({
        connector: () => new ReplaySubject(1),
        resetOnError: true,
        resetOnComplete: false,
        resetOnRefCountZero: false,
      })
    );
  }

  private getLoadCall(
    i: InstanceDto,
    g: InstanceGroupConfiguration
  ): Observable<InstanceStateRecord> {
    return !i || !g
      ? of(null)
      : this.http
          .get<InstanceStateRecord>(
            `${this.apiPath(g.name)}/${i.instanceConfiguration.uuid}/state`
          )
          .pipe(measure('Load Instance Version States'));
  }

  public install(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/install`
      )
      .pipe(
        measure(
          `Install ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }

  public uninstall(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/uninstall`
      )
      .pipe(
        measure(
          `Uninstall ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }

  public activate(version: string): Observable<any> {
    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name)}/${
          this.instances.current$.value.instanceConfiguration.uuid
        }/${version}/activate`
      )
      .pipe(
        measure(
          `Activate ${this.instances.current$.value.instanceConfiguration.uuid} Version ${version}`
        )
      );
  }
}
