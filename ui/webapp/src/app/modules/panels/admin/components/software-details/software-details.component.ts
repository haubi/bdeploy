import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import {
  SoftwareUpdateService,
  SoftwareVersion,
} from 'src/app/modules/primary/admin/services/software-update.service';

@Component({
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
})
export class SoftwareDetailsComponent implements OnDestroy {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);

  /* template */ software$ = new BehaviorSubject<SoftwareVersion>(null);

  /* template */ systemOs$ = new BehaviorSubject<OperatingSystem[]>(null);
  /* template */ launcherOs$ = new BehaviorSubject<OperatingSystem[]>(null);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(areas: NavAreasService, private software: SoftwareUpdateService) {
    this.subscription = combineLatest([
      areas.panelRoute$,
      software.software$,
    ]).subscribe(([r, s]) => {
      if (!r?.params || !r.params['version'] || !s) return;

      const version = r.params['version'];
      const sw = s.find((x) => x.version === version);
      this.software$.next(sw);

      this.systemOs$.next(sw.system.map((x) => getAppOs(x)));
      this.launcherOs$.next(sw.launcher.map((x) => getAppOs(x)));
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doDelete() {
    this.dialog
      .confirm(
        'Delete Version',
        `This will delete all associated system and launcher versions for each operating system.`,
        'delete'
      )
      .subscribe((r) => {
        if (!r) return;

        this.deleting$.next(true);
        this.software
          .deleteVersion([
            ...this.software$.value.system,
            ...this.software$.value.launcher,
          ])
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.tb.closePanel();
            this.software.load();
          });
      });
  }

  /* template */ doInstall() {
    this.dialog
      .confirm(
        'Install Version',
        `Installing this version will cause a short downtime, typically a few seconds.`,
        'system_update'
      )
      .subscribe((r) => {
        if (!r) return;

        this.installing$.next(true);
        this.software
          .updateBdeploy(this.software$.value.system)
          .pipe(finalize(() => this.installing$.next(false)))
          .subscribe(() => {
            this.software.load();
          });
      });
  }
}
