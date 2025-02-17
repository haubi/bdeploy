import { Injectable } from '@angular/core';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
} from 'src/app/models/data';
import { InstanceGroupConfigurationDto } from 'src/app/models/gen.dtos';
import { GroupsService } from './groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsColumnsService {
  groupTypeColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: () => 'Instance Group',
    display: BdDataColumnDisplay.CARD,
  };

  groupNameColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceGroupConfiguration.name,
    width: '200px',
    showWhen: '(min-width: 700px)',
  };

  groupTitleColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'title',
    name: 'Title',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceGroupConfiguration.title,
  };

  groupDescriptionColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceGroupConfiguration.description,
    showWhen: '(min-width: 1000px)',
  };

  groupLogoTableColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.TABLE,
    data: (r) =>
      this.groups.getLogoUrlOrDefault(
        r.instanceGroupConfiguration.name,
        r.instanceGroupConfiguration.logo,
        null
      ),
    width: '150px',
  };

  groupLogoCardColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: (r) =>
      this.groups.getLogoUrlOrDefault(
        r.instanceGroupConfiguration.name,
        r.instanceGroupConfiguration.logo,
        '/assets/no-image.svg'
      ),
  };

  defaultGroupColumns: BdDataColumn<InstanceGroupConfigurationDto>[] = [
    this.groupTypeColumn,
    this.groupNameColumn,
    this.groupTitleColumn,
    this.groupDescriptionColumn,
    this.groupLogoTableColumn,
    this.groupLogoCardColumn,
  ];

  constructor(private groups: GroupsService) {}
}
