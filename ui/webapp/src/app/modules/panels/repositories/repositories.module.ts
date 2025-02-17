import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { AddRepositoryComponent } from './components/add-repository/add-repository.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { PermissionsComponent } from './components/settings/permissions/permissions.component';
import { SettingsComponent } from './components/settings/settings.component';
import { SoftwareDetailsComponent } from './components/software-details/software-details.component';
import { SoftwareUploadComponent } from './components/software-upload/software-upload.component';
import { RepositoriesRoutingModule } from './repositories-routing.module';

@NgModule({
  declarations: [
    AddRepositoryComponent,
    SettingsComponent,
    EditComponent,
    PermissionsComponent,
    SoftwareUploadComponent,
    SoftwareDetailsComponent,
  ],
  imports: [CommonModule, CoreModule, RepositoriesRoutingModule],
})
export class RepositoriesModule {}
