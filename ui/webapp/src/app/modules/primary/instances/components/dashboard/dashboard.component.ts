import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);

  /* template */ serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  /* template */ clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  /* template */ allApplications$ = new BehaviorSubject<ApplicationConfiguration[]>([]);

  /* template */ gridMode$ = new BehaviorSubject<boolean>(false);
  /* template */ grouping$ = new BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>([]);

  /* template */ groupingDefinitions: BdDataGroupingDefinition<ApplicationConfiguration>[] = [];

  /* template */ collapsed$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  constructor(
    private media: BreakpointObserver,
    public instances: InstancesService,
    public areas: NavAreasService,
    public cfg: ConfigService,
    public servers: ServersService
  ) {}

  ngOnInit(): void {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
    this.subscription.add(
      this.instances.activeNodeCfgs$.subscribe((nodes) => {
        if (!nodes?.nodeConfigDtos?.length) {
          this.serverNodes$.next([]);
          this.clientNode$.next(null);
          this.allApplications$.next([]);
          return;
        }

        this.serverNodes$.next(
          nodes.nodeConfigDtos
            .filter((p) => p.nodeName !== CLIENT_NODE_NAME && !!p.nodeConfiguration?.applications?.length)
            .sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName))
        );

        this.allApplications$.next([].concat(nodes.nodeConfigDtos.map((x) => (!!x?.nodeConfiguration?.applications ? x.nodeConfiguration.applications : []))));
        this.clientNode$.next(nodes.nodeConfigDtos.find((p) => p.nodeName === CLIENT_NODE_NAME && p.nodeConfiguration?.applications?.length));
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
