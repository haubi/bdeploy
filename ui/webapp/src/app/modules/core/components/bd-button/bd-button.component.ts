import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { TooltipPosition } from '@angular/material/tooltip';
import { fromEvent } from 'rxjs';
import { delayedFadeIn } from '../../animations/fades';
import { scaleWidthFromZero } from '../../animations/sizes';

@Component({
  selector: 'app-bd-button',
  templateUrl: './bd-button.component.html',
  styleUrls: ['./bd-button.component.css'],
  animations: [delayedFadeIn, scaleWidthFromZero],
})
export class BdButtonComponent implements OnInit, AfterViewInit {
  @Input() icon: string;
  @Input() text: string;
  @Input() tooltip: TooltipPosition;
  @Input() badge: number;
  @Input() badgeColor: ThemePalette = 'accent';
  @Input() collapsed = true;
  @Input() inverseColor = false;
  @Input() disabled = false;

  @Input() isToggle = false;
  @Input() toggle = false;
  @Output() toggleChange = new EventEmitter<boolean>();

  constructor(public _elementRef: ElementRef) {}

  ngOnInit(): void {}

  ngAfterViewInit() {
    /*
     * Click event handler for the button. we bind on the host, NOT on the button intentionally
     * as otherwise events will still be generated even if the button is disabled.
     */
    fromEvent<MouseEvent>(this._elementRef.nativeElement, 'click', { capture: true }).subscribe((event) => {
      if (this.isToggle) {
        this.toggle = !this.toggle;
        this.toggleChange.emit(this.toggle);
        event.stopPropagation();
      } else {
        if (this.disabled) {
          event.stopPropagation();
        }
      }
    });
  }
}
