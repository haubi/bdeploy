import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-bd-search-field',
  templateUrl: './bd-search-field.component.html',
  styleUrls: ['./bd-search-field.component.css'],
})
export class BdSearchFieldComponent implements OnInit, OnDestroy {
  @Input() disabled = false;

  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();

  @ViewChild('searchField') searchField: ElementRef;

  private searchChanged = new Subject<string>();
  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.searchChanged
      .pipe(debounceTime(200))
      .subscribe((v) => this.valueChange.emit(v));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ queueChange() {
    this.searchChanged.next(this.value);
  }

  @HostListener('window:keydown.control.f', ['$event'])
  setFocus(event: KeyboardEvent) {
    if (this.disabled) {
      return;
    }
    this.searchField.nativeElement.focus();
    event.preventDefault();
  }
}
