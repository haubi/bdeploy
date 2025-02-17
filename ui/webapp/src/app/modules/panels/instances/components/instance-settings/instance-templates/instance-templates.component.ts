import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import {
  BehaviorSubject,
  combineLatest,
  Observable,
  of,
  Subscription,
} from 'rxjs';
import { concatAll, finalize, first, map, skipWhile } from 'rxjs/operators';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationType,
  InstanceNodeConfigurationDto,
  InstanceTemplateDescriptor,
  InstanceTemplateGroup,
  ProcessControlGroupConfiguration,
  ProductDto,
  TemplateApplication,
} from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import {
  getAppKeyName,
  getTemplateAppKey,
} from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../../services/process-edit.service';
import { TemplateMessageDetailsComponent } from './template-message-details/template-message-details.component';

export interface TemplateMessage {
  group: string;
  node: string;
  appname: string;
  template: TemplateApplication;
  message: StatusMessage;
}

const tplColName: BdDataColumn<TemplateMessage> = {
  id: 'name',
  name: 'Name',
  data: (r) => (r.appname ? r.appname : `${r.group}/${r.node}`),
};

const tplColDetails: BdDataColumn<TemplateMessage> = {
  id: 'details',
  name: 'Details',
  data: (r) => r.message.message,
  component: TemplateMessageDetailsComponent,
  width: '36px',
};

@Component({
  selector: 'app-instance-templates',
  templateUrl: './instance-templates.component.html',
  styleUrls: ['./instance-templates.component.css'],
})
export class InstanceTemplatesComponent implements OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(false);

  /* template */ records: InstanceTemplateDescriptor[];
  /* template */ recordsLabel: string[];

  /* template */ template: InstanceTemplateDescriptor;
  /* template */ variables: { [key: string]: string }; // key is var name, value is value.
  /* template */ groups: { [key: string]: string }; // key is group name, value is target node name.
  /* template */ messages: TemplateMessage[];
  /* template */ msgColumns: BdDataColumn<TemplateMessage>[] = [
    tplColName,
    tplColDetails,
  ];
  /* template */ isAnyGroupSelected = false;
  /* template */ hasAllVariables = false;
  /* template */ firstStepCompleted = false;
  /* template */ secondStepCompleted = false;

  /* template */ groupNodes: { [key: string]: string[] };
  /* template */ groupLabels: { [key: string]: string[] };

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('msgTemplate') private tplMessages: TemplateRef<any>;
  @ViewChild('stepper', { static: false }) private myStepper: MatStepper;

  private product: ProductDto;
  private subscription: Subscription;

  constructor(
    public servers: ServersService,
    public instanceEdit: InstanceEditService,
    private products: ProductsService,
    private edit: ProcessEditService
  ) {
    this.subscription = combineLatest([
      this.instanceEdit.state$,
      this.products.products$,
    ]).subscribe(([state, prods]) => {
      const prod = prods?.find(
        (p) =>
          p.key.name === state?.config?.config?.product.name &&
          p.key.tag === state?.config?.config?.product.tag
      );

      if (!prod) {
        this.records = [];
        return;
      }

      this.product = prod;
      this.records = prod.instanceTemplates ? prod.instanceTemplates : [];
      this.recordsLabel = this.records.map((record) => record.name);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getNodesFor(group: InstanceTemplateGroup): string[] {
    if (group.type === ApplicationType.CLIENT) {
      return [null, CLIENT_NODE_NAME];
    } else {
      return [
        null,
        // eslint-disable-next-line no-unsafe-optional-chaining
        ...this.instanceEdit.state$.value?.config?.nodeDtos
          .map((n) => n.nodeName)
          .filter((n) => n !== CLIENT_NODE_NAME),
      ];
    }
  }

  private getLabelsFor(group: InstanceTemplateGroup): string[] {
    const nodeValues = this.getNodesFor(group);

    return nodeValues.map((n) => {
      if (n === null) {
        return '(skip)';
      } else if (n === CLIENT_NODE_NAME) {
        return 'Apply to Client Applications';
      } else {
        return 'Apply to ' + n;
      }
    });
  }

  public validateAnyGroupSelected() {
    for (const k of Object.keys(this.groups)) {
      const v = this.groups[k];
      if (v !== null && v !== undefined) {
        this.isAnyGroupSelected = true;
        return;
      }
    }
    this.isAnyGroupSelected = false;
  }

  public validateHasAllVariables() {
    if (!this.template) {
      return;
    }
    for (const v of this.template.variables) {
      const value = this.variables[v.uid];
      if (value === '' || value === null || value === undefined) {
        this.hasAllVariables = false;
        return;
      }
    }
    this.hasAllVariables = true;
  }

  /* template */ selectTemplate() {
    // setup things required by the templates.
    this.groupNodes = {};
    this.groupLabels = {};
    this.template.groups.forEach((group) => {
      this.groupNodes[group.name] = this.getNodesFor(group);
      this.groupLabels[group.name] = this.getLabelsFor(group);
    });
    this.groups = {};

    this.validateAnyGroupSelected();
    this.firstStepCompleted = true;
    this.goNext();
  }

  /* template */ goToAssignVariableStep() {
    this.secondStepCompleted = true;
    this.goNext();
  }

  private goNext() {
    this.myStepper.selected.completed = true;
    this.myStepper.next();
  }

  public applyStageFinal() {
    this.loading$.next(true);
    this.messages = [];
    const observables = [];

    // prepare available process control groups
    const pcgs = this.template.processControlGroups.map(
      (p) => Object.assign({}, p) as ProcessControlGroupConfiguration
    );
    pcgs.forEach((p) => (p.processOrder = []));

    for (const groupName of Object.keys(this.groups)) {
      const nodeName = this.groups[groupName];
      if (!nodeName) {
        continue; // skipped.
      }

      const node = this.instanceEdit.state$.value?.config?.nodeDtos?.find(
        (n) => n.nodeName === nodeName
      );
      const group = this.template.groups.find((g) => g.name === groupName);

      if (!node || !group) {
        this.messages.push({
          group: groupName,
          node: nodeName,
          appname: null,
          template: null,
          message: { icon: 'warning', message: 'Cannot find node or group' },
        });
        continue;
      }

      // not set or SERVER
      if (group.type !== ApplicationType.CLIENT) {
        // for servers, we need to find the appropriate application with the correct OS.
        this.applyServerGroup(
          group,
          node,
          pcgs,
          groupName,
          nodeName,
          observables
        );
      } else {
        // for clients we add all matches, regardless of the OS.
        this.applyClientGroup(group, observables, node, groupName, nodeName);
      }
    }

    if (!observables.length) {
      return; // nothing to do...?
    }

    const templateName = this.template.name; // will be reset in the process.

    // now execute and await all additions.
    combineLatest(observables)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe(() => {
        this.instanceEdit.state$.value?.config?.nodeDtos.forEach((n) =>
          this.cleanProcessControlGroup(n)
        );

        let applyResult = of(true);
        // now if we DO have messages, we want to show them to the user.
        if (this.messages.length) {
          console.log(this.messages);
          applyResult = this.dialog.message({
            header: 'Template Messages',
            template: this.tplMessages,
            dismissResult: null,
            actions: [ACTION_CANCEL, ACTION_OK],
          });
        } else {
          this.tb.closePanel();
        }

        applyResult.subscribe((r) => {
          if (r) {
            this.instanceEdit.conceal(
              `Apply instance template ${templateName}`
            );
          } else {
            this.instanceEdit.discard();
          }
        });
      });
  }

  private applyClientGroup(
    group: InstanceTemplateGroup,
    observables: Observable<string>[],
    node: InstanceNodeConfigurationDto,
    groupName: string,
    nodeName: string
  ) {
    for (const template of group.applications) {
      // need to find all apps in the product which match the key name...
      const searchKey = this.product.product + '/' + template.application;
      const status = [];
      for (const app of this.instanceEdit.stateApplications$.value) {
        const appKey = getAppKeyName(app.key);
        if (searchKey === appKey) {
          observables.push(
            this.edit
              .addProcess(node, app, template, this.variables, status)
              .pipe(
                finalize(() => {
                  status.forEach((e) =>
                    this.messages.push({
                      group: groupName,
                      node: nodeName,
                      appname: template?.name ? template.name : app.name,
                      template: template,
                      message: e,
                    })
                  );
                })
              )
          );
        }
      }
    }
  }

  private applyServerGroup(
    group: InstanceTemplateGroup,
    node: InstanceNodeConfigurationDto,
    pcgs: ProcessControlGroupConfiguration[],
    groupName: string,
    nodeName: string,
    observables: Observable<string>[]
  ) {
    for (const app of group.applications) {
      // need to prepare process control groups synchronously before adding applications.
      this.prepareProcessControlGroups(app, node, pcgs, groupName, nodeName);

      observables.push(
        this.instanceEdit.nodes$.pipe(
          // wait for node information
          skipWhile((n) => !n),
          // pick the first valid node info
          first(),
          // map the node info to the application key we need for our node.
          map((n) =>
            this.instanceEdit.stateApplications$.value?.find(
              (a) =>
                a.key.name === getTemplateAppKey(this.product, app, n[nodeName])
            )
          ),
          // map the key of the app to an observable to actually add the application if possible.
          map((a) => {
            if (!a) {
              this.messages.push({
                group: groupName,
                node: nodeName,
                template: app,
                appname: app?.name
                  ? app.name
                  : app.template
                  ? app.template
                  : app.application,
                message: {
                  icon: 'warning',
                  message: 'Cannot find application in product for target OS.',
                },
              });
              return of<string>(null);
            } else {
              const status = [];
              return this.edit
                .addProcess(node, a, app, this.variables, status)
                .pipe(
                  finalize(() => {
                    status.forEach((e) =>
                      this.messages.push({
                        group: groupName,
                        node: nodeName,
                        appname: app?.name ? app.name : a.name,
                        template: app,
                        message: e,
                      })
                    );
                  })
                );
            }
          }),
          // since adding returns an observable we concat them, so a subscription to the observable will yield the addProcess response.
          concatAll()
        )
      );
    }
  }

  /**
   * Prepare process control groups for the given application on the node.
   */
  private prepareProcessControlGroups(
    app: TemplateApplication,
    node: InstanceNodeConfigurationDto,
    pcgs: ProcessControlGroupConfiguration[],
    groupName: string,
    nodeName: string
  ) {
    const cg = app.preferredProcessControlGroup;
    if (cg) {
      const existingCg = node.nodeConfiguration.controlGroups.find(
        (n) => n.name === cg
      );
      if (!existingCg) {
        // need to prepare the group.
        const pcgTempl = pcgs.find((p) => p.name === cg);
        if (!pcgTempl) {
          this.messages.push({
            group: groupName,
            node: nodeName,
            appname: null,
            template: null,
            message: {
              icon: 'warning',
              message: `Cannot find template for requested process control group ${cg}`,
            },
          });
        } else {
          // TODO: check order of groups...? Which order is relevant? Order of groups in template?
          node.nodeConfiguration.controlGroups.push(pcgTempl);
        }
      }
    }
  }

  /**
   * Removes empty process control groups from the configuration.
   */
  private cleanProcessControlGroup(node: InstanceNodeConfigurationDto) {
    node.nodeConfiguration.controlGroups =
      node.nodeConfiguration.controlGroups.filter(
        (n) => !!n.processOrder?.length
      );
  }

  /* template */ onStepSelectionChange(event: StepperSelectionEvent) {
    switch (event.selectedIndex) {
      case 0:
        this.groups = {};
        this.firstStepCompleted = false;
        this.secondStepCompleted = false;

        this.template = null;
        this.groupLabels = null;
        this.groupNodes = null;
        break;
      case 1:
        this.secondStepCompleted = false;
        this.variables = {};

        if (this.template.variables?.length) {
          for (const v of this.template.variables) {
            this.variables[v.uid] = v.defaultValue;
          }
        }
        break;
    }
  }
}
