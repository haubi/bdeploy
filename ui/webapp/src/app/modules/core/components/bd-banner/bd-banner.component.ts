import { Component, Input } from '@angular/core';
import { InstanceBannerRecord } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-bd-banner',
  templateUrl: './bd-banner.component.html',
  styleUrls: ['./bd-banner.component.css'],
})
export class BdBannerComponent {
  @Input() banner: InstanceBannerRecord;
}
