import { Component, Inject, InjectionToken } from '@angular/core';
import { MinionMode, Version } from 'src/app/models/gen.dtos';
import { convert2String } from '../../utils/version.utils';

export interface VersionMismatch {
  oldVersion: Version;
  newVersion: Version;
  mode: MinionMode;
}

export const VERSION_DATA = new InjectionToken<VersionMismatch>('VERSION_DATA');

@Component({
  selector: 'app-connection-version',
  templateUrl: './connection-version.component.html',
  styleUrls: ['./connection-version.component.css'],
})
export class ConnectionVersionComponent {
  /* template */ newVersion: string;
  /* template */ oldVersion: string;
  /* template */ mode: MinionMode;

  constructor(@Inject(VERSION_DATA) private data: VersionMismatch) {
    this.newVersion = convert2String(data.newVersion);
    this.oldVersion = convert2String(data.oldVersion);
    this.mode = data.mode;
  }

  onReload(): void {
    window.location.reload();
  }
}
