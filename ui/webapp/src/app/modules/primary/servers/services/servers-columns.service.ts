import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataSyncCellComponent } from '../../../core/components/bd-data-sync-cell/bd-data-sync-cell.component';

@Injectable({
  providedIn: 'root',
})
export class ServersColumnsService {
  serverNameColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.hostName,
    hint: BdDataColumnTypeHint.TITLE,
  };

  serverDescColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'description',
    name: 'Description',
    data: (r) => r.description,
    hint: BdDataColumnTypeHint.DESCRIPTION,
  };

  serverNodesColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'nodeCount',
    name: 'Nodes',
    data: (r) => Object.keys(r.minions.minions).length,
    icon: (r) => 'dock',
    hint: BdDataColumnTypeHint.DETAILS,
  };

  serverSyncTimeColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'syncTime',
    name: 'Last Sync.',
    data: (r) => r.lastSync,
    icon: () => 'history',
    hint: BdDataColumnTypeHint.DETAILS,
    component: BdDataDateCellComponent,
  };

  serverSyncColumn: BdDataColumn<ManagedMasterDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => r,
    component: BdDataSyncCellComponent,
    width: '50px',
  };

  public defaultServerColumns: BdDataColumn<ManagedMasterDto>[] = [
    this.serverNameColumn,
    this.serverDescColumn,
    this.serverNodesColumn,
    this.serverSyncTimeColumn,
    this.serverSyncColumn,
  ];

  public defaultReducedServerColumns: BdDataColumn<ManagedMasterDto>[] = [
    this.serverNameColumn,
    this.serverDescColumn,
  ];
}
