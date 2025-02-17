import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  ProcessDetailDto,
  RemoteDirectory,
  RemoteDirectoryEntry,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NO_LOADING_BAR } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessesService } from 'src/app/modules/primary/instances/services/processes.service';

@Injectable({
  providedIn: 'root',
})
export class ProcessDetailsService implements OnDestroy {
  public loading$ = new BehaviorSubject<boolean>(true);
  public processDetail$ = new BehaviorSubject<ProcessDetailDto>(null);
  public processConfig$ = new BehaviorSubject<ApplicationConfiguration>(null);

  private subscription: Subscription;

  private apiPath = (group, instance) =>
    `${this.cfg.config.api}/group/${group}/instance/${instance}`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    private instances: InstancesService,
    private processes: ProcessesService,
    private areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.processes.processStates$,
      this.instances.active$,
      this.instances.activeNodeCfgs$,
    ]).subscribe(([route, states, instance, nodes]) => {
      // check preconditions to do anything at all :) a lot of data needs to be available.
      const process = route?.params['process'];
      if (!process || !instance || !nodes) {
        this.processConfig$.next(null);
        this.processDetail$.next(null);
        this.loading$.next(false);
        return;
      }

      // find the configuration for the application we're showing details for
      const appsPerNode = nodes.nodeConfigDtos.map((x) =>
        x?.nodeConfiguration?.applications
          ? x.nodeConfiguration.applications
          : []
      );
      const allApps: ApplicationConfiguration[] = [].concat(...appsPerNode);
      const app = allApps.find((a) => a?.uid === process);

      this.processConfig$.next(app);

      if (!states || !states[process]) {
        this.processDetail$.next(null);
        return;
      }

      // now load the status details and popuplate the service data.
      this.http
        .get<ProcessDetailDto>(
          `${this.apiPath(
            this.groups.current$.value.name,
            instance.instanceConfiguration.uuid
          )}/processes/${process}`,
          NO_LOADING_BAR
        )
        .pipe(
          finalize(() => this.loading$.next(false)),
          measure(`Process Details`)
        )
        .subscribe((d) => {
          this.processDetail$.next(d);
        });
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public writeStdin(value: string) {
    const detail = this.processDetail$.value;

    if (!detail.hasStdin) {
      return;
    }

    this.http
      .post(
        `${this.apiPath(
          this.groups.current$.value.name,
          this.instances.active$.value.instanceConfiguration.uuid
        )}/processes/${detail.status.appUid}/stdin`,
        value
      )
      .subscribe();
  }

  public getOutputEntry(): Observable<[RemoteDirectory, RemoteDirectoryEntry]> {
    const detail = this.processDetail$.value;
    const instance = this.instances.active$.value;

    return this.http
      .get<RemoteDirectory>(
        `${this.apiPath(
          this.groups.current$.value.name,
          instance.instanceConfiguration.uuid
        )}/output/${instance.instance.tag}/${detail.status.appUid}`,
        NO_LOADING_BAR
      )
      .pipe(
        map((e) => {
          return [e, e.entries[0]];
        })
      );
  }
}
