import { Component, OnDestroy, ViewChild } from '@angular/core';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  HistoryEntryDto,
  HistoryEntryType,
  InstanceStateRecord,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { HistoryService } from 'src/app/modules/primary/instances/services/history.service';
import { InstanceStateService } from 'src/app/modules/primary/instances/services/instance-state.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { histKey, histKeyDecode } from '../../utils/history-key.utils';

@Component({
  selector: 'app-history-entry',
  templateUrl: './history-entry.component.html',
})
export class HistoryEntryComponent implements OnDestroy {
  /* template */ entry$ = new BehaviorSubject<HistoryEntryDto>(null);
  /* template */ state$ = new BehaviorSubject<InstanceStateRecord>(null);

  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ uninstalling$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ isCreate: boolean;
  /* template */ isInstalled: boolean;
  /* template */ isActive: boolean;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private history: HistoryService,
    public instances: InstancesService,
    public states: InstanceStateService,
    public servers: ServersService,
    public auth: AuthenticationService
  ) {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.history.history$,
      this.states.state$,
    ]).subscribe(([route, entries, state]) => {
      // Note: basing the selection on an index in the service has some drawbacks, but we can do that now without needing to change a lot in the backend.
      const key = route?.paramMap?.get('key');
      this.state$.next(state);
      if (!key || !entries) {
        this.entry$.next(null);
      } else {
        const entry = entries.find((e) =>
          isEqual(histKey(e), histKeyDecode(key))
        );
        this.entry$.next(entry);
        this.isCreate = entry.type === HistoryEntryType.CREATE;
        this.isInstalled = !!state?.installedTags?.find(
          (s) => s === entry?.instanceTag
        );
        this.isActive = state?.activeTag === entry?.instanceTag;
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doInstall() {
    this.installing$.next(true);
    this.states
      .install(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  /* template */ doUninstall() {
    this.uninstalling$.next(true);
    this.states
      .uninstall(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.uninstalling$.next(false)))
      .subscribe();
  }

  /* template */ doActivate() {
    this.activating$.next(true);
    this.states
      .activate(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }

  /* template */ doExport() {
    this.instances.export(this.entry$.value.instanceTag);
  }

  /* template */ doDelete() {
    this.dialog
      .confirm(
        `Delete Version`,
        `This instance version and all its history will be deleted and <strong>cannot be restored</strong>. Are you sure you want to do this?`,
        'delete'
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }
        this.deleting$.next(true);
        this.instances
          .deleteVersion(this.entry$.value.instanceTag)
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe();
      });
  }
}
