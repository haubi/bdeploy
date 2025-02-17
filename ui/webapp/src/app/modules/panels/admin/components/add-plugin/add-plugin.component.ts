import { Component } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { UploadStatus } from 'src/app/modules/core/services/upload.service';
import { PluginAdminService } from 'src/app/modules/primary/admin/services/plugin-admin.service';

@Component({
  selector: 'app-add-plugin',
  templateUrl: './add-plugin.component.html',
})
export class AddPluginComponent {
  /* template */ files: File[] = [];

  /* template */ resultEvaluator(result: UploadStatus): string {
    if (!result.detail) {
      return null;
    }

    const details = result.detail as PluginInfoDto;
    return 'Added ' + details.name + ' ' + details.version;
  }

  constructor(public plugins: PluginAdminService) {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
