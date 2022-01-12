import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable, of, Subscription } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { InstancePurpose } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-edit-config',
  templateUrl: './edit-config.component.html',
  styleUrls: ['./edit-config.component.css'],
})
export class EditConfigComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription: Subscription;

  /* template */ purposes = [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
  /* template */ hasPendingChanges: boolean;

  constructor(public cfg: ConfigService, public edit: InstanceEditService, public servers: ServersService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {}

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.hasPendingChanges = this.isDirty();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap((x) => {
        this.edit.conceal('Change Instance Configuration');
      })
    );
  }
}
