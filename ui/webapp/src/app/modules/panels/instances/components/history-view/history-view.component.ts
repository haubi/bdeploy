import { Component, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { InstanceNodeConfigurationListDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  BdSearchable,
  SearchService,
} from 'src/app/modules/core/services/search.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { InstanceConfigCache } from '../../utils/instance-utils';

@Component({
  selector: 'app-history-view',
  templateUrl: './history-view.component.html',
  styleUrls: ['./history-view.component.css'],
})
export class HistoryViewComponent implements OnDestroy, BdSearchable {
  /* template */ base$ = new BehaviorSubject<string>(null);
  /* template */ config$ = new BehaviorSubject<InstanceConfigCache>(null);
  /* template */ clientNodeName = CLIENT_NODE_NAME;
  /* template */ searchTerm = '';

  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private details: HistoryDetailsService,
    public instances: InstancesService,
    private searchService: SearchService
  ) {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      const base = route?.paramMap?.get('base');
      if (!base) {
        this.base$.next(null);
      } else {
        this.base$.next(base);
        this.details.getVersionDetails(base).subscribe((config) => {
          this.config$.next(config);
        });
      }
    });

    this.subscription.add(this.searchService.register(this));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  bdOnSearch(value: string) {
    this.searchTerm = value;
  }

  /* template */ getAppDesc(
    nodes: InstanceNodeConfigurationListDto,
    name: string
  ) {
    return nodes?.applications.find((a) => a.key.name === name)?.descriptor;
  }
}
