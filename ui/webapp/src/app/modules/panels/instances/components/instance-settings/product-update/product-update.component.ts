import { Component, OnDestroy } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ProductVersionDetailsComponent } from './product-version-details/product-version-details.component';
import { UpdateActionComponent } from './update-action/update-action.component';

@Component({
  selector: 'app-product-update',
  templateUrl: './product-update.component.html',
})
export class ProductUpdateComponent implements OnDestroy {
  private readonly productVersionColumn: BdDataColumn<ProductDto> = {
    id: 'version',
    name: 'Version',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) =>
      `${r.key.tag}${
        this.edit.state$.value.config.config.product.tag === r.key.tag
          ? ' - current'
          : ''
      }`,
    tooltip: () => null,
    component: ProductVersionDetailsComponent,
  };

  private readonly productUpdateAction: BdDataColumn<ProductDto> = {
    id: 'update',
    name: 'Upd.',
    data: (r) => r.key.tag,
    component: UpdateActionComponent,
    width: '40px',
  };

  /* template */ records$ = new BehaviorSubject<ProductDto[]>(null);
  /* template */ columns: BdDataColumn<ProductDto>[] = [
    this.productVersionColumn,
    this.productUpdateAction,
  ];

  private subscription: Subscription;

  constructor(
    public products: ProductsService,
    private edit: InstanceEditService
  ) {
    this.subscription = combineLatest([
      this.edit.state$,
      this.products.products$,
    ]).subscribe(([state, prods]) => {
      if (!state || !prods?.length) {
        this.records$.next(null);
        return;
      }

      this.records$.next(
        prods.filter((p) => p.key.name === state.config.config.product.name)
      );
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
