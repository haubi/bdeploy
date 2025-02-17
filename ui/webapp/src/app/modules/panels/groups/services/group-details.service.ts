import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CustomAttributesRecord,
  InstanceGroupConfiguration,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupDetailsService {
  private hiveApiPath = `${this.cfg.config.api}/hive`;
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService
  ) {}

  public delete(group: InstanceGroupConfiguration): Observable<any> {
    return this.http.delete(`${this.apiPath(group.name)}`);
  }

  public update(group: InstanceGroupConfiguration): Observable<any> {
    return this.http.post(this.apiPath(group.name), group);
  }

  public updateAttributes(group: string, attributes: CustomAttributesRecord) {
    return this.http.post(`${this.apiPath(group)}/attributes`, attributes);
  }

  public prune(hive: string) {
    return this.http.get(`${this.hiveApiPath}/prune`, {
      params: { hive },
      responseType: 'text',
    });
  }

  public repair(hive: string) {
    return this.http.get<Map<string, string>>(`${this.hiveApiPath}/fsck`, {
      params: { hive, fix: 'true' },
    });
  }
}
