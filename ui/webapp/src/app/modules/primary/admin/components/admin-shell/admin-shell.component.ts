import { Component } from '@angular/core';
import { ConfigService } from 'src/app/modules/core/services/config.service';

@Component({
  selector: 'app-admin-shell',
  templateUrl: './admin-shell.component.html',
  styleUrls: ['./admin-shell.component.css'],
})
export class AdminShellComponent {
  constructor(public cfg: ConfigService) {}
}
