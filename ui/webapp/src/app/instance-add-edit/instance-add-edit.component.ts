import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { MatButton } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { finalize, map, startWith } from 'rxjs/operators';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { EMPTY_INSTANCE } from '../models/consts';
import { InstanceConfiguration, InstancePurpose, InstanceVersionDto, ProductDto } from '../models/gen.dtos';
import { InstanceGroupService } from '../services/instance-group.service';
import { InstanceService } from '../services/instance.service';
import { Logger, LoggingService } from '../services/logging.service';
import { MessageboxService } from '../services/messagebox.service';
import { ProductService } from '../services/product.service';
import { sortByTags } from '../utils/manifest.utils';
import { InstanceValidators } from '../validators/instance.validators';

@Component({
  selector: 'app-instance-add-edit',
  templateUrl: './instance-add-edit.component.html',
  styleUrls: ['./instance-add-edit.component.css'],
})
export class InstanceAddEditComponent implements OnInit {
  private log: Logger = this.loggingService.getLogger('InstanceAddEditComponent');

  public groupParam: string;
  public uuidParam: string;

  public purposes: InstancePurpose[];
  public products: ProductDto[] = [];

  public masterURLsLoaded = false;
  public masterURLs: string[] = [];

  public loading = false;
  public isNewMasterUrl = false;
  public filteredMasterURLs: Observable<string[]>;

  public updateMasterTokenChecked = false;
  public masterTokenCheckboxVisible = false;
  public masterTokenControlVisible = false;

  public clonedInstance: InstanceConfiguration;
  private expectedVersion: InstanceVersionDto;

  public instanceFormGroup = this.formBuilder.group({
    uuid: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    autoStart: [''],
    configTree: [''],
    purpose: ['', Validators.required],
    product: this.formBuilder.group({
      name: ['', Validators.required],
      tag: ['', Validators.required],
    }),
    target: this.formBuilder.group({
      uri: ['', [Validators.required, InstanceValidators.urlPattern]],
      authPack: ['', Validators.required],
    }),
    autoUninstall: [''],
  });

  private overlayRef: OverlayRef;

  constructor(
    private formBuilder: FormBuilder,
    private instanceGroupService: InstanceGroupService,
    private productService: ProductService,
    private instanceService: InstanceService,
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    private messageBoxService: MessageboxService,
    public location: Location,
    private viewContainerRef: ViewContainerRef,
    private overlay: Overlay,
  ) {}

  ngOnInit() {
    this.groupParam = this.route.snapshot.paramMap.get('group');
    this.uuidParam = this.route.snapshot.paramMap.get('uuid');
    this.log.debug('groupParam = ' + this.groupParam + ', nameParam = ' + this.uuidParam);

    if (this.isCreate()) {
      this.instanceGroupService.createUuid(this.groupParam).subscribe((uuid: string) => {
        this.log.debug('got uuid ' + uuid);
        const instance = cloneDeep(EMPTY_INSTANCE);
        instance.autoUninstall = true;
        instance.uuid = uuid;
        this.instanceFormGroup.setValue(instance);
        this.clonedInstance = cloneDeep(instance);
      });
    } else {
      this.instanceService.getInstance(this.groupParam, this.uuidParam).subscribe(instance => {
        this.log.debug('got instance ' + this.uuidParam);
        this.instanceFormGroup.setValue(instance);
        this.clonedInstance = cloneDeep(instance);
      });
      // TODO: this could be better (group with above request)
      this.instanceService.listInstanceVersions(this.groupParam, this.uuidParam).subscribe(vs => {
        vs.sort((a, b) => {
          return +b.key.tag - +a.key.tag;
        });
        this.expectedVersion = vs[0];
      });
    }

    this.instanceService.listPurpose(this.groupParam).subscribe(purposes => {
      this.purposes = purposes;
      this.log.debug('got purposes ' + this.purposes);
    });

    this.productService.getProducts(this.groupParam).subscribe(products => {
      this.products = products;
      this.log.debug('got ' + products.length + ' products');
    });

    this.instanceGroupService.getMasterUrls(this.groupParam).subscribe(masterUrls => {
      this.masterURLs = masterUrls;
      this.masterURLsLoaded = true;
      this.log.debug('got master URLs ' + JSON.stringify(this.masterURLs));
      this.updateTokenVisibilityAndValidity();

      this.filteredMasterURLs = this.masterUrlControl.valueChanges.pipe(
        startWith(''),
        map(input => {
          const inputToLowerCase = input == null ? null : input.toLowerCase();
          return this.masterURLs.filter(uri => input == null || uri.toLowerCase().includes(inputToLowerCase));
        }),
      );
    });

    // check master on input
    this.masterUrlControl.valueChanges.subscribe(input => this.updateTokenVisibilityAndValidity());

    // Clear tag if product changed
    this.productNameControl.valueChanges.subscribe(input => this.productTagControl.reset());

    // Changing product and tag is not allowed after instance has been created
    // User needs to change that in the process config dialog as all validation and update logic is located there
    if (!this.isCreate()) {
      this.productNameControl.disable();
      this.productTagControl.disable();
    }
  }

  isModified(): boolean {
    const instance: InstanceConfiguration = this.instanceFormGroup.getRawValue();
    return !isEqual(instance, this.clonedInstance);
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isModified()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Instance was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  public getErrorMessage(ctrl: FormControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('urlPattern')) {
      return 'Requires https://<host>:<port>/api';
    }
    return 'Unknown error';
  }

  public isCreate(): boolean {
    return this.uuidParam == null;
  }

  public getProductNames(): string[] {
    const pNames = Array.from(this.products, p => p.key.name);
    return pNames.filter((value, index, array) => array.indexOf(value) === index).sort();
  }

  public getProductDisplayName(pName: string): string {
    const product: ProductDto = this.products.find(e => e.key.name === pName);
    return product.name + (product.vendor ? ' (' + product.vendor + ')' : '');
  }

  public getTagsForProduct(): string[] {
    const selectedProduct = this.productNameControl.value;
    const productVersions = this.products.filter((value, index, array) => value.key.name === selectedProduct);
    return sortByTags(Array.from(productVersions, p => p.key.tag), p => p, false);
  }

  public onSubmit(): void {
    this.loading = true;
    const instance: InstanceConfiguration = this.instanceFormGroup.getRawValue();

    if (this.isCreate()) {
      this.instanceService
        .createInstance(this.groupParam, instance)
        .pipe(finalize(() => (this.loading = false)))
        .subscribe(result => {
          this.clonedInstance = instance;
          this.log.info('created new instance ' + this.uuidParam);
          this.location.back();
        });
    } else {
      this.instanceService
        .updateInstance(this.groupParam, this.uuidParam, instance, null, this.expectedVersion.key.tag)
        .pipe(finalize(() => (this.loading = false)))
        .subscribe(result => {
          this.clonedInstance = instance;
          this.log.info('updated instance ' + this.uuidParam);
          this.location.back();
        });
    }
  }

  public toggleUpdateMasterToken(): void {
    this.updateMasterTokenChecked = !this.updateMasterTokenChecked;
    this.updateTokenVisibilityAndValidity();
  }

  public updateTokenVisibilityAndValidity(): void {
    // Do not perform anything until we loaded the master URLS
    if (!this.masterURLsLoaded) {
      return;
    }

    // Is the master URL one of the known ones?
    let masterUrlValue = <string>this.masterUrlControl.value;
    if (masterUrlValue != null) {
      masterUrlValue = masterUrlValue.trim();
    }
    this.isNewMasterUrl = !this.masterURLs.some(e => e === masterUrlValue);

    // Token field is visible and mandatory for unknown master URLS
    if (this.isCreate()) {
      this.masterTokenCheckboxVisible = false;
      this.masterTokenControlVisible = this.isNewMasterUrl;
    } else {
      // Token field is visible when requested by the user
      // or when the master URL has changed
      this.masterTokenCheckboxVisible = !this.isNewMasterUrl;
      this.masterTokenControlVisible = this.isNewMasterUrl || this.updateMasterTokenChecked;
    }

    // Field is mandatory if it is visible
    if (this.masterTokenControlVisible) {
      this.masterTokenControl.setValidators(Validators.required);
    } else {
      this.masterTokenControl.clearValidators();
    }
    this.masterTokenControl.updateValueAndValidity();
  }

  get uuidControl() {
    return this.instanceFormGroup.get('uuid');
  }
  get nameControl() {
    return this.instanceFormGroup.get('name');
  }
  get descriptionControl() {
    return this.instanceFormGroup.get('description');
  }
  get purposeControl() {
    return this.instanceFormGroup.get('purpose');
  }

  get productNameControl() {
    return this.instanceFormGroup.get('product.name');
  }
  get productTagControl() {
    return this.instanceFormGroup.get('product.tag');
  }
  get masterUrlControl() {
    return this.instanceFormGroup.get('target.uri');
  }
  get masterTokenControl() {
    return this.instanceFormGroup.get('target.authPack');
  }

  openOverlay(relative: MatButton, template: TemplateRef<any>) {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(relative._elementRef)
        .withPositions([
          {
            overlayX: 'end',
            overlayY: 'bottom',
            originX: 'center',
            originY: 'top',
          },
        ])
        .withPush()
        .withViewportMargin(0)
        .withDefaultOffsetX(35)
        .withDefaultOffsetY(-10),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: true,
      backdropClass: 'info-backdrop',
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  /** Closes the overlay if present */
  closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

}
