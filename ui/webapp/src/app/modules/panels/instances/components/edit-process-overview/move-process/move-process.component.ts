import { Component, OnDestroy } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationType,
  InstanceNodeConfigurationDto,
  OperatingSystem,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { updateAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

interface NodeRow {
  name: string;
  type: ApplicationType;
  os: OperatingSystem;
  current: boolean;
  node: InstanceNodeConfigurationDto;
}

const colNodeName: BdDataColumn<NodeRow> = {
  id: 'name',
  name: 'Node',
  data: (r) => `${r.name}${r.current ? ' - Current' : ''}`,
  classes: (r) => (r.current ? ['bd-disabled-text'] : []),
};

@Component({
  selector: 'app-move-process',
  templateUrl: './move-process.component.html',
})
export class MoveProcessComponent implements OnDestroy {
  /* template */ records: NodeRow[] = [];
  /* template */ columns: BdDataColumn<NodeRow>[] = [colNodeName];

  private currentNode: InstanceNodeConfigurationDto;
  private subscription: Subscription;

  constructor(
    public instanceEdit: InstanceEditService,
    public edit: ProcessEditService,
    private areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.instanceEdit.state$,
      this.edit.application$,
      this.edit.process$,
      this.instanceEdit.nodes$,
    ]).subscribe(([state, app, process, nodes]) => {
      if (!state || !app || !process || !nodes) {
        this.records = [];
        this.currentNode = null;
        return;
      }

      this.currentNode = state.config.nodeDtos.find(
        (n) =>
          !!n.nodeConfiguration.applications.find((a) => a.uid === process.uid)
      );

      const result: NodeRow[] = [];
      for (const node of state.config.nodeDtos) {
        const nodeType =
          node.nodeName === CLIENT_NODE_NAME
            ? ApplicationType.CLIENT
            : ApplicationType.SERVER;
        if (app.descriptor.type !== nodeType) {
          continue;
        }

        let nodeOs = null;
        if (nodeType === ApplicationType.SERVER) {
          const nodeDetails = nodes[node.nodeName];
          if (!nodeDetails) {
            continue;
          }

          nodeOs = nodeDetails.os;
          if (!app.descriptor.supportedOperatingSystems.includes(nodeOs)) {
            continue;
          }
        }

        const name = this.niceName(node.nodeName);
        result.push({
          name: name,
          current: node.nodeName === this.currentNode.nodeName,
          node: node,
          os: nodeOs,
          type: nodeType,
        });
      }

      this.records = result;
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private niceName(node: string) {
    return node === CLIENT_NODE_NAME ? 'Client Applications' : node;
  }

  /* template */ onSelectNode(node: NodeRow) {
    if (node.current) {
      // prevent move to self.
      return;
    }

    const cfg = this.edit.process$.value;

    const targetNode = this.instanceEdit.state$.value?.config?.nodeDtos?.find(
      (n) => n.nodeName === node.name
    )?.nodeConfiguration;
    const targetApps = targetNode?.applications;

    if (!targetNode) {
      return;
    }

    // remove the current process.
    this.edit.removeProcess();

    if (node.type !== ApplicationType.CLIENT) {
      updateAppOs(cfg.application, node.os);
    }

    targetApps.push(cfg);
    this.instanceEdit
      .getLastControlGroup(targetNode)
      .processOrder.push(cfg.uid);
    this.instanceEdit.conceal(
      `Move ${cfg.name} from ${this.niceName(this.currentNode.nodeName)} to ${
        node.name
      }`
    );

    // this edit is so severe that none of the panels (edit overview, etc.) will work as data is shifted. close panels completely.
    this.areas.closePanel();
  }
}
