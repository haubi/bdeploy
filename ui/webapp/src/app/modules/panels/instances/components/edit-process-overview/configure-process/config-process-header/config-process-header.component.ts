import { SelectionModel } from '@angular/cdk/collections';
import { FlatTreeControl } from '@angular/cdk/tree';
import {
  AfterViewInit,
  Component,
  EventEmitter,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import {
  MatTreeFlatDataSource,
  MatTreeFlattener,
} from '@angular/material/tree';
import { debounceTime, skipWhile, Subscription } from 'rxjs';
import {
  ApplicationDto,
  ApplicationStartType,
  ConfigDirDto,
} from 'src/app/models/gen.dtos';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProcessEditService } from '../../../../services/process-edit.service';

export class DirTreeNode {
  name: string;
  level: number;
  expandable: boolean;
}

@Component({
  selector: 'app-config-process-header',
  templateUrl: './config-process-header.component.html',
  styleUrls: ['./config-process-header.component.css'],
})
export class ConfigProcessHeaderComponent
  implements OnInit, OnDestroy, AfterViewInit
{
  @ViewChild('form') public form: NgForm;
  @ViewChild('dirSelector', { static: false })
  private dirSelector: BdPopupDirective;
  @Output() checkIsInvalid = new EventEmitter<boolean>();

  /* template */ app: ApplicationDto;
  /* template */ startTypes: ApplicationStartType[];
  /* template */ startTypeLabels: string[];

  /* template */ dirFlattener: MatTreeFlattener<ConfigDirDto, DirTreeNode>;
  /* tempalte */ dirTreeControl: FlatTreeControl<DirTreeNode>;
  /* template */ dirDataSource: MatTreeFlatDataSource<
    ConfigDirDto,
    DirTreeNode
  >;
  /* template */ dirSelection = new SelectionModel<DirTreeNode>(
    true /* multiple */
  );
  /* template */ dirFlatAllowedValues: string[];

  private subscription: Subscription;

  constructor(
    public edit: ProcessEditService,
    private instances: InstancesService
  ) {
    this.dirFlattener = new MatTreeFlattener<ConfigDirDto, DirTreeNode>(
      this.dirTransformer,
      (n) => n.level,
      (n) => n.expandable,
      (n) => n.children
    );
    this.dirTreeControl = new FlatTreeControl(
      (n) => n.level,
      (n) => n.expandable
    );
    this.dirDataSource = new MatTreeFlatDataSource(
      this.dirTreeControl,
      this.dirFlattener
    );
  }

  dirTransformer = (node: ConfigDirDto, level: number): DirTreeNode => {
    console.log({ node: node });
    return {
      name: node.name,
      level: level,
      expandable: !!node.children?.length,
    };
  };

  hasChild = (_: number, _nodeData: DirTreeNode) => {
    return _nodeData.expandable;
  };

  ngOnInit(): void {
    this.subscription = this.edit.application$.subscribe((application) => {
      this.app = application;
      this.startTypes = this.getStartTypes(this.app);
      this.startTypeLabels = this.getStartTypeLabels(this.app);
    });

    this.subscription.add(
      this.instances.current$.pipe(skipWhile((i) => !i)).subscribe((i) => {
        this.dirDataSource.data = !i.configRoot ? [] : [i.configRoot];
        this.dirTreeControl.dataNodes
          .filter((n) => n.level === 0)
          .forEach((n) => this.dirTreeControl.expand(n));
        this.dirFlatAllowedValues = [
          '/',
          ...this.buildFlatAllowedValues(i.configRoot, ['']),
        ];
      })
    );
  }

  private buildFlatAllowedValues(dir: ConfigDirDto, path: string[]): string[] {
    if (!dir) {
      return [];
    }
    if (dir.children?.length) {
      return dir.children.flatMap((c) =>
        this.buildFlatAllowedValues(c, [...path, c.name])
      );
    } else {
      return [path.join('/')];
    }
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.statusChanges.pipe(debounceTime(100)).subscribe((status) => {
        this.checkIsInvalid.emit(status !== 'VALID');
      })
    );
  }

  /* template */ onDirSelectorOpened(dirs: string) {
    this.dirSelection.clear();

    if (!dirs?.trim()?.length) {
      return; // empty - nothing to select.
    }

    const toSelect =
      dirs === '/'
        ? ['/']
        : (dirs || '').split(',').map((d) => d.trim().split('/'));
    toSelect.forEach((n) => this.selectLeaf(n));
  }

  private selectLeaf(path: string[]) {
    const rootNodes = this.dirTreeControl.dataNodes.filter(
      (n) => n.level === 0
    );
    rootNodes.forEach((n) => this.findLeafAndSelect(n, path));
  }

  private findLeafAndSelect(node: DirTreeNode, path: string[]) {
    if (
      path?.length < node.level ||
      (node.level > 0 && node.name !== path[node.level])
    ) {
      return; // nope
    }

    // we're on the right track.
    if (path.length === node.level + 1 && !node.expandable) {
      this.dirSelection.select(node);
      return;
    }

    // need to go one level deeper.
    if (node.expandable) {
      this.dirTreeControl
        .getDescendants(node)
        .filter((n) => n.level === node.level + 1)
        .forEach((n) => this.findLeafAndSelect(n, path));
    }
  }

  /* template */ doApplyDirectories() {
    const rootNodes = this.dirTreeControl.dataNodes.filter(
      (n) => n.level === 0
    );
    const selectedLeafs: string[] = [];
    for (const root of rootNodes) {
      // find selected leafs. empty root will join to '/'
      selectedLeafs.push(...this.getSelectedLeafPaths(root, ['']));
    }
    this.edit.process$.value.processControl.configDirs =
      selectedLeafs.join(',');
    this.dirSelector.closeOverlay();
  }

  private getSelectedLeafPaths(node: DirTreeNode, path: string[]): string[] {
    if (
      this.dirSelection.isSelected(node) ||
      this.dirTreeControl
        .getDescendants(node)
        .some((n) => this.dirSelection.isSelected(n))
    ) {
      if (node.expandable) {
        return this.dirTreeControl
          .getDescendants(node)
          .filter((n) => n.level === node.level + 1)
          .flatMap((n) => this.getSelectedLeafPaths(n, [...path, n.name]));
      } else {
        if (path?.length === 1 && path[0] === '') {
          // root node.
          return ['/'];
        }
        // single leaf node, build path.
        return [path.join('/')];
      }
    }
    return [];
  }

  /** Whether all the descendants of the node are selected */
  /* template */ descendantsAllSelected(node: DirTreeNode): boolean {
    const descendants = this.dirTreeControl.getDescendants(node);
    return descendants.every((child) => this.dirSelection.isSelected(child));
  }

  /** Whether part of the descendants are selected */
  /* template */ descendantsPartiallySelected(node: DirTreeNode): boolean {
    const descendants = this.dirTreeControl.getDescendants(node);
    const result = descendants.some((child) =>
      this.dirSelection.isSelected(child)
    );
    return result && !this.descendantsAllSelected(node);
  }

  /** Toggle the directory item selection. Select/deselect all the descendants node */
  /* template */ dirItemSelectionToggle(node: DirTreeNode): void {
    this.dirSelection.toggle(node);
    const descendants = this.dirTreeControl.getDescendants(node);
    this.dirSelection.isSelected(node)
      ? this.dirSelection.select(...descendants)
      : this.dirSelection.deselect(...descendants);
  }

  private getStartTypes(app: ApplicationDto): ApplicationStartType[] {
    const supported = app?.descriptor?.processControl?.supportedStartTypes;
    if (
      !supported?.length ||
      !!supported.find((s) => s === ApplicationStartType.INSTANCE)
    ) {
      return [
        ApplicationStartType.INSTANCE,
        ApplicationStartType.MANUAL,
        ApplicationStartType.MANUAL_CONFIRM,
      ];
    } else if (supported.find((s) => s === ApplicationStartType.MANUAL)) {
      return [ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else {
      return supported;
    }
  }

  private getStartTypeLabels(app: ApplicationDto): string[] {
    return this.getStartTypes(app).map((t) => {
      switch (t) {
        case ApplicationStartType.INSTANCE:
          return 'Instance (Automatic)';
        case ApplicationStartType.MANUAL:
          return 'Manual';
        case ApplicationStartType.MANUAL_CONFIRM:
          return 'Manual (with confirmation)';
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
