import { Component, OnInit } from '@angular/core';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesService } from '../../services/repositories.service';
import { RepositoryColumnsService } from '../../services/repository-columns.service';
import { RepositoryService } from '../../services/repository.service';

@Component({
  selector: 'app-repository',
  templateUrl: './repository.component.html',
})
export class RepositoryComponent implements OnInit {
  grouping: BdDataGroupingDefinition<any>[] = [
    { name: 'Type', group: (r) => r.type },
  ];
  defaultGrouping: BdDataGrouping<any>[] = [
    { definition: this.grouping[0], selected: [] },
  ];

  /* template */ getRecordRoute = (row: any) => {
    return [
      '',
      {
        outlets: {
          panel: [
            'panels',
            'repositories',
            'details',
            row.key.name,
            row.key.tag,
          ],
        },
      },
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'software-repository';

  constructor(
    public repositories: RepositoriesService,
    public repository: RepositoryService,
    public repositoryColumns: RepositoryColumnsService,
    public auth: AuthenticationService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit() {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
