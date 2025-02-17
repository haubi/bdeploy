import {
  Component,
  ComponentRef,
  Input,
  OnDestroy,
  OnInit,
  Type,
  ViewContainerRef,
} from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-component-cell',
  templateUrl: './bd-data-component-cell.component.html',
})
export class BdDataComponentCellComponent<T, X> implements OnInit, OnDestroy {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
  @Input() componentType: Type<X>;
  private componentRef: ComponentRef<X>;

  constructor(private vc: ViewContainerRef) {}

  ngOnInit(): void {
    this.vc.clear();
    this.componentRef = this.vc.createComponent<X>(this.componentType);
    this.componentRef.instance['record'] = this.record;
    this.componentRef.instance['column'] = this.column;
  }

  ngOnDestroy(): void {
    if (!this.componentRef) {
      this.componentRef.destroy();
    }
  }
}
