import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { ClientAppsComponent } from './components/client-apps/client-apps.component';
import { InstanceGroupAddEditComponent } from './components/instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './components/instance-group-browser/instance-group-browser.component';
import { InstanceGroupDeleteDialogComponent } from './components/instance-group-delete-dialog/instance-group-delete-dialog.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { ProductsComponent } from './components/products/products.component';
import { InstanceGroupRoutingModule } from './instance-group-routing.module';

@NgModule({
  declarations: [
    ProductsComponent,
    InstanceGroupBrowserComponent,
    InstanceGroupAddEditComponent,
    InstanceGroupDeleteDialogComponent,
    ProductCardComponent,
    ProductListComponent,
    ClientAppsComponent,
  ],
  entryComponents: [
    InstanceGroupDeleteDialogComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    InstanceGroupRoutingModule
  ],
})
export class InstanceGroupModule { }
