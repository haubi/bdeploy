import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  Observable,
  of,
  Subscription,
} from 'rxjs';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'edit-global-attribute',
  templateUrl: './edit-global-attribute.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditGlobalAttributeComponent
  implements OnInit, OnDestroy, DirtyableDialog
{
  /* template */ tempAttribute: CustomAttributeDescriptor;
  /* template */ origAttribute: CustomAttributeDescriptor;
  /* template */ initialAttribute: CustomAttributeDescriptor;
  /* template */ tempUsedIds: string[];
  /* template */ loading$ = new BehaviorSubject<boolean>(true);

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  constructor(
    private settings: SettingsService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([
        this.areas.panelRoute$,
        this.settings.settings$,
      ]).subscribe(([route, settings]) => {
        if (!settings || !route?.params || !route.params['attribute']) {
          return;
        }

        this.initialAttribute = settings.instanceGroup.attributes.find(
          (a) => a.name === route.params['attribute']
        );
        this.tempAttribute = cloneDeep(this.initialAttribute);
        this.origAttribute = cloneDeep(this.initialAttribute);
        this.tempUsedIds = settings.instanceGroup.attributes
          .map((a) => a.name)
          .filter((a) => a !== this.initialAttribute.name);
        this.loading$.next(false);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  isDirty(): boolean {
    if (!this.tempAttribute) {
      return false;
    }
    return isDirty(this.tempAttribute, this.initialAttribute);
  }

  canSave(): boolean {
    return this.form.valid;
  }

  public doSave(): Observable<void> {
    return of(
      this.settings.editGlobalAttribute(
        this.tempAttribute,
        this.initialAttribute
      )
    );
  }

  private reset() {
    this.tempAttribute = this.initialAttribute;
    this.areas.closePanel();
  }
}
