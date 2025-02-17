import { Component } from '@angular/core';
import { map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { PluginAdminService } from '../../../services/plugin-admin.service';
import { PluginDeleteActionComponent } from './plugin-delete-action/plugin-delete-action.component';
import { PluginLoadActionComponent } from './plugin-load-action/plugin-load-action.component';

@Component({
  selector: 'app-plugins-tab',
  templateUrl: './plugins-tab.component.html',
})
export class PluginsTabComponent {
  private colId: BdDataColumn<PluginInfoDto> = {
    id: 'id',
    name: 'ID',
    data: (r) => r.id.id,
    width: '70px',
    showWhen: '(min-width: 1000px)',
  };

  private colName: BdDataColumn<PluginInfoDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
  };

  private colVersion: BdDataColumn<PluginInfoDto> = {
    id: 'version',
    name: 'Version',
    data: (r) => r.version,
    width: '100px',
  };

  private colLoaded: BdDataColumn<PluginInfoDto> = {
    id: 'loaded',
    name: 'Loaded',
    data: (r) => (r.loaded ? 'check_box' : 'check_box_outline_blank'),
    component: BdDataIconCellComponent,
    width: '40px',
  };

  private colGlobal: BdDataColumn<PluginInfoDto> = {
    id: 'global',
    name: 'Global',
    data: (r) => (r.global ? 'public' : null),
    component: BdDataIconCellComponent,
    width: '40px',
  };

  private colLoadUnload: BdDataColumn<PluginInfoDto> = {
    id: 'loadUnload',
    name: 'Ctrl',
    data: (r) => r,
    component: PluginLoadActionComponent,
    width: '40px',
  };

  private colDelete: BdDataColumn<PluginInfoDto> = {
    id: 'delete',
    name: 'Del.',
    data: (r) => r,
    component: PluginDeleteActionComponent,
    width: '40px',
  };

  /* template */ columns: BdDataColumn<PluginInfoDto>[] = [
    this.colId,
    this.colName,
    this.colVersion,
    this.colGlobal,
    this.colLoaded,
    this.colLoadUnload,
    this.colDelete,
  ];

  /* template */ plugins$ = this.plugins.plugins$.pipe(
    map((data) => this.sortPlugins(data))
  );

  constructor(public plugins: PluginAdminService) {}

  private sortPlugins(data: PluginInfoDto[]): PluginInfoDto[] {
    return [...data].sort((a, b) => {
      const x = a?.name?.localeCompare(b?.name);
      if (x !== 0) {
        return x;
      }
      return a?.version?.localeCompare(b?.version);
    });
  }
}
